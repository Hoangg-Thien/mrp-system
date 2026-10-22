package mrp.production.service;

import mrp.bom.entity.BomItem;
import mrp.bom.repository.BomItemRepository;
import mrp.catalog.entity.Material;
import mrp.catalog.repository.MaterialRepository;
import mrp.common.exception.BusinessException;
import mrp.common.exception.ResourceNotFoundException;
import mrp.inventory.entity.MovementType;
import mrp.inventory.service.StockLedger;
import mrp.production.dto.ProductionQuery;
import mrp.production.dto.ProductionRequests;
import mrp.production.dto.ProductionViews;
import mrp.production.entity.MaterialIssueRequest;
import mrp.production.entity.MaterialIssueRequestItem;
import mrp.production.entity.MaterialIssueRequestStatus;
import mrp.production.entity.ProductionRequest;
import mrp.production.entity.ProductionRequestStatus;
import mrp.production.repository.MaterialIssueRequestItemRepository;
import mrp.production.repository.MaterialIssueRequestRepository;
import mrp.production.repository.ProductionRequestRepository;
import mrp.security.CurrentUser;
import mrp.user.entity.Staff;
import mrp.user.entity.User;
import mrp.user.entity.UserRole;
import mrp.user.entity.WarehouseManager;
import mrp.user.repository.StaffRepository;
import mrp.user.repository.UserRepository;
import mrp.user.repository.WarehouseManagerRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dịch vụ xử lý vòng đời Phiếu xuất nguyên vật liệu (MaterialIssueRequest).
 */
@Service
public class MaterialIssueService {

    private final ProductionRequestRepository prRepo;
    private final MaterialIssueRequestRepository mirRepo;
    private final MaterialIssueRequestItemRepository mirItemRepo;
    private final BomItemRepository bomItemRepo;
    private final MaterialRepository materialRepo;
    private final StaffRepository staffRepo;
    private final UserRepository userRepo;
    private final WarehouseManagerRepository wmRepo;
    private final StockLedger stockLedger;
    private final JdbcTemplate jdbc;

    public MaterialIssueService(
            ProductionRequestRepository prRepo,
            MaterialIssueRequestRepository mirRepo,
            MaterialIssueRequestItemRepository mirItemRepo,
            BomItemRepository bomItemRepo,
            MaterialRepository materialRepo,
            StaffRepository staffRepo,
            UserRepository userRepo,
            WarehouseManagerRepository wmRepo,
            StockLedger stockLedger,
            JdbcTemplate jdbc) {
        this.prRepo = prRepo;
        this.mirRepo = mirRepo;
        this.mirItemRepo = mirItemRepo;
        this.bomItemRepo = bomItemRepo;
        this.materialRepo = materialRepo;
        this.staffRepo = staffRepo;
        this.userRepo = userRepo;
        this.wmRepo = wmRepo;
        this.stockLedger = stockLedger;
        this.jdbc = jdbc;
    }

    public BigDecimal getApprovedIssuedQuantity(Long prId, Long materialId) {
        BigDecimal issued = jdbc.queryForObject("""
            SELECT COALESCE(SUM(miri.quantity), 0)
              FROM material_issue_request_items miri
              JOIN material_issue_requests mir ON mir.id = miri.request_id
             WHERE mir.production_request_id = ?
               AND miri.material_id = ?
               AND mir.status = 'APPROVED'
            """, BigDecimal.class, prId, materialId);
        return issued != null ? issued : BigDecimal.ZERO;
    }

    @Transactional
    public ProductionViews.MaterialIssueRequestDetailView createMaterialIssueRequest(
            ProductionRequests.CreateMaterialIssueRequest req, CurrentUser actor) {

        ProductionRequest pr = prRepo.findById(req.productionRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu sản xuất #" + req.productionRequestId()));

        // Phải là người được giao (D11 / trigger trg_mir_actor)
        if (pr.getAssignedTo() == null || !pr.getAssignedTo().equals(actor.profileId())) {
            throw new ResourceNotFoundException("Không tìm thấy yêu cầu sản xuất #" + req.productionRequestId());
        }

        if (pr.getStatus() != ProductionRequestStatus.PENDING && pr.getStatus() != ProductionRequestStatus.IN_PROGRESS) {
            throw new BusinessException("Yêu cầu sản xuất #" + pr.getId() + " đang " + pr.getStatus() + ", không thể tạo phiếu xuất NVL");
        }

        List<BomItem> bomItems = bomItemRepo.findByBomIdOrderByMaterialIdAsc(pr.getBomId());
        Map<Long, BigDecimal> bomQtyMap = bomItems.stream()
                .collect(Collectors.toMap(BomItem::getMaterialId, BomItem::getQuantity));

        Set<Long> seen = new HashSet<>();
        boolean hasShortage = false;

        for (ProductionRequests.IssueItemRequest itemReq : req.items()) {
            if (!seen.add(itemReq.materialId())) {
                throw new BusinessException("Nguyên vật liệu #" + itemReq.materialId() + " bị trùng lặp trong yêu cầu");
            }
            if (!bomQtyMap.containsKey(itemReq.materialId())) {
                throw new BusinessException("Nguyên vật liệu #" + itemReq.materialId() + " không nằm trong BOM của yêu cầu sản xuất");
            }

            BigDecimal bomQty = bomQtyMap.get(itemReq.materialId());
            BigDecimal maxQuota = bomQty.multiply(BigDecimal.valueOf(pr.getQuantity())).setScale(3, RoundingMode.HALF_UP);

            BigDecimal existingPendingOrApproved = jdbc.queryForObject("""
                SELECT COALESCE(SUM(miri.quantity), 0)
                  FROM material_issue_request_items miri
                  JOIN material_issue_requests mir ON mir.id = miri.request_id
                 WHERE mir.production_request_id = ?
                   AND miri.material_id = ?
                   AND mir.status IN ('PENDING', 'APPROVED')
                """, BigDecimal.class, pr.getId(), itemReq.materialId());
            if (existingPendingOrApproved == null) existingPendingOrApproved = BigDecimal.ZERO;

            if (existingPendingOrApproved.add(itemReq.quantity()).compareTo(maxQuota) > 0) {
                throw new BusinessException("Tổng số lượng yêu cầu NVL (" + existingPendingOrApproved.add(itemReq.quantity())
                        + ") vượt định mức BOM (" + maxQuota + ")");
            }

            // Kiểm tra tồn kho để cảnh báo (D3: cho phép tạo, kèm cảnh báo)
            BigDecimal balance = stockLedger.materialBalance(itemReq.materialId());
            if (balance.compareTo(itemReq.quantity()) < 0) {
                hasShortage = true;
            }
        }

        // Tạo header trước
        MaterialIssueRequest mir = new MaterialIssueRequest(pr.getId(), actor.profileId(), req.note());
        mir = mirRepo.saveAndFlush(mir);

        // Tạo dòng chi tiết
        for (ProductionRequests.IssueItemRequest itemReq : req.items()) {
            MaterialIssueRequestItem item = new MaterialIssueRequestItem(mir.getId(), itemReq.materialId(), itemReq.quantity());
            mirItemRepo.save(item);
        }
        mirItemRepo.flush();

        String warning = hasShortage ? "Kho hiện tại không đủ tồn cho một số nguyên vật liệu trong phiếu" : null;
        return detailMaterialIssueRequest(mir.getId(), actor, hasShortage, warning);
    }

    public Page<ProductionViews.MaterialIssueRequestView> listMaterialIssueRequests(
            Long productionRequestId, MaterialIssueRequestStatus status, Long requestedBy,
            int page, int size, String sort, String direction, CurrentUser actor, boolean isStaff) {

        PageRequest pageRequest = ProductionQuery.page(page, size, sort, direction, ProductionQuery.MIR_SORTS);
        final Long effectiveRequestedBy = isStaff ? actor.profileId() : requestedBy;

        Specification<MaterialIssueRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (productionRequestId != null) {
                predicates.add(cb.equal(root.get("productionRequestId"), productionRequestId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (effectiveRequestedBy != null) {
                predicates.add(cb.equal(root.get("requestedBy"), effectiveRequestedBy));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<MaterialIssueRequest> mirPage = mirRepo.findAll(spec, pageRequest);
        if (mirPage.isEmpty()) {
            return mirPage.map(m -> null);
        }

        Set<Long> staffIds = mirPage.getContent().stream().map(MaterialIssueRequest::getRequestedBy).collect(Collectors.toSet());
        Map<Long, Staff> staffMap = staffRepo.findAllById(staffIds).stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        Set<Long> wmIds = mirPage.getContent().stream().map(MaterialIssueRequest::getReviewedBy).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, WarehouseManager> wmMap = wmRepo.findAllById(wmIds).stream()
                .collect(Collectors.toMap(WarehouseManager::getId, w -> w));

        return mirPage.map(mir -> {
            Staff s = staffMap.get(mir.getRequestedBy());
            WarehouseManager wm = wmMap.get(mir.getReviewedBy());
            int count = mirItemRepo.findByRequestId(mir.getId()).size();

            return new ProductionViews.MaterialIssueRequestView(
                mir.getId(),
                mir.getProductionRequestId(),
                mir.getRequestedBy(),
                s != null ? s.getFullName() : null,
                mir.getReviewedBy(),
                wm != null ? wm.getFullName() : null,
                mir.getStatus().name(),
                mir.getRequestedAt(),
                mir.getReviewedAt(),
                mir.getNote(),
                count
            );
        });
    }

    public ProductionViews.MaterialIssueRequestDetailView detailMaterialIssueRequest(Long id, CurrentUser actor) {
        return detailMaterialIssueRequest(id, actor, false, null);
    }

    public ProductionViews.MaterialIssueRequestDetailView detailMaterialIssueRequest(
            Long id, CurrentUser actor, boolean hasShortage, String warning) {

        MaterialIssueRequest mir = mirRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu xuất NVL #" + id));

        // STAFF chỉ xem được phiếu của mình (D11: 404)
        if (actor != null && !actor.profileId().equals(mir.getRequestedBy())) {
            User user = userRepo.findById(actor.userId()).orElse(null);
            if (user != null && user.getRole() == UserRole.STAFF) {
                throw new ResourceNotFoundException("Không tìm thấy phiếu xuất NVL #" + id);
            }
        }

        Staff requester = staffRepo.findById(mir.getRequestedBy()).orElse(null);
        WarehouseManager reviewer = mir.getReviewedBy() != null ? wmRepo.findById(mir.getReviewedBy()).orElse(null) : null;

        List<MaterialIssueRequestItem> items = mirItemRepo.findByRequestId(mir.getId());
        Set<Long> matIds = items.stream().map(MaterialIssueRequestItem::getMaterialId).collect(Collectors.toSet());
        Map<Long, Material> matMap = materialRepo.findAllById(matIds).stream()
                .collect(Collectors.toMap(Material::getId, m -> m));

        boolean shortageDetected = hasShortage;
        List<ProductionViews.MaterialIssueItemView> itemViews = new ArrayList<>();

        for (MaterialIssueRequestItem item : items) {
            Material m = matMap.get(item.getMaterialId());
            BigDecimal currentStock = stockLedger.materialBalance(item.getMaterialId());
            if (currentStock.compareTo(item.getQuantity()) < 0) {
                shortageDetected = true;
            }
            itemViews.add(new ProductionViews.MaterialIssueItemView(
                item.getId(),
                item.getMaterialId(),
                m != null ? m.getCode() : null,
                m != null ? m.getName() : null,
                m != null ? m.getUnit() : null,
                item.getQuantity(),
                currentStock
            ));
        }

        if (shortageDetected && warning == null && mir.getStatus() == MaterialIssueRequestStatus.PENDING) {
            warning = "Kho hiện tại không đủ tồn cho một số nguyên vật liệu trong phiếu";
        }

        return new ProductionViews.MaterialIssueRequestDetailView(
            mir.getId(),
            mir.getProductionRequestId(),
            mir.getRequestedBy(),
            requester != null ? requester.getFullName() : null,
            mir.getReviewedBy(),
            reviewer != null ? reviewer.getFullName() : null,
            mir.getStatus().name(),
            mir.getRequestedAt(),
            mir.getReviewedAt(),
            mir.getNote(),
            shortageDetected,
            warning,
            itemViews
        );
    }

    @Transactional
    public ProductionViews.MaterialIssueRequestDetailView approveMaterialIssueRequest(Long id, CurrentUser actor) {
        MaterialIssueRequest mir = mirRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu xuất NVL #" + id));

        if (mir.getStatus() != MaterialIssueRequestStatus.PENDING) {
            throw new BusinessException("Chỉ được duyệt phiếu ở trạng thái PENDING (hiện tại: " + mir.getStatus() + ")");
        }

        List<MaterialIssueRequestItem> items = mirItemRepo.findByRequestId(id);

        // Pre-check tồn kho từng dòng: nếu thiếu 1 dòng thì rollback toàn bộ, không movement nào
        for (MaterialIssueRequestItem item : items) {
            BigDecimal balance = stockLedger.materialBalance(item.getMaterialId());
            if (balance.compareTo(item.getQuantity()) < 0) {
                throw new BusinessException("Tồn kho nguyên vật liệu #" + item.getMaterialId()
                        + " không đủ để xuất kho (hiện có: " + balance + ", yêu cầu: " + item.getQuantity() + ")");
            }
        }

        mir.setStatus(MaterialIssueRequestStatus.APPROVED);
        mir.setReviewedBy(actor.profileId());
        mir.setReviewedAt(LocalDateTime.now());
        mirRepo.saveAndFlush(mir);

        // Ghi stock_movements OUT từng dòng
        for (MaterialIssueRequestItem item : items) {
            stockLedger.post(
                MovementType.OUT,
                item.getMaterialId(),
                null,
                item.getQuantity(),
                null,
                mir.getId(),
                null,
                null,
                actor.userId(),
                "Xuất NVL theo phiếu xuất #" + mir.getId()
            );
        }

        return detailMaterialIssueRequest(id, actor);
    }

    @Transactional
    public ProductionViews.MaterialIssueRequestDetailView rejectMaterialIssueRequest(
            Long id, ProductionRequests.RejectRequest req, CurrentUser actor) {

        MaterialIssueRequest mir = mirRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu xuất NVL #" + id));

        if (mir.getStatus() != MaterialIssueRequestStatus.PENDING) {
            throw new BusinessException("Chỉ được từ chối phiếu ở trạng thái PENDING (hiện tại: " + mir.getStatus() + ")");
        }

        mir.setStatus(MaterialIssueRequestStatus.REJECTED);
        mir.setReviewedBy(actor.profileId());
        mir.setReviewedAt(LocalDateTime.now());
        String originalNote = mir.getNote() != null && !mir.getNote().isBlank() ? mir.getNote() + " " : "";
        mir.setNote(originalNote + "[TỪ CHỐI] " + req.reason().trim());
        mirRepo.saveAndFlush(mir);

        return detailMaterialIssueRequest(id, actor);
    }

    @Transactional
    public ProductionViews.MaterialIssueRequestDetailView cancelMaterialIssueRequest(Long id, CurrentUser actor) {
        MaterialIssueRequest mir = mirRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu xuất NVL #" + id));

        if (!mir.getRequestedBy().equals(actor.profileId())) {
            throw new ResourceNotFoundException("Không tìm thấy phiếu xuất NVL #" + id);
        }

        if (mir.getStatus() != MaterialIssueRequestStatus.PENDING) {
            throw new BusinessException("Chỉ được hủy phiếu ở trạng thái PENDING (hiện tại: " + mir.getStatus() + ")");
        }

        mir.setStatus(MaterialIssueRequestStatus.CANCELLED);
        mirRepo.saveAndFlush(mir);

        return detailMaterialIssueRequest(id, actor);
    }
}
