package mrp.production.service;

import mrp.bom.entity.Bom;
import mrp.bom.entity.BomItem;
import mrp.bom.repository.BomItemRepository;
import mrp.bom.repository.BomRepository;
import mrp.catalog.entity.Material;
import mrp.catalog.entity.Product;
import mrp.catalog.repository.MaterialRepository;
import mrp.catalog.repository.ProductRepository;
import mrp.catalog.service.CatalogService;
import mrp.common.exception.BusinessException;
import mrp.common.exception.ResourceNotFoundException;
import mrp.inventory.service.StockLedger;
import mrp.production.dto.ProductionQuery;
import mrp.production.dto.ProductionRequests;
import mrp.production.dto.ProductionViews;
import mrp.production.entity.MaterialIssueRequest;
import mrp.production.entity.MaterialIssueRequestStatus;
import mrp.production.entity.Production;
import mrp.production.entity.ProductionRequest;
import mrp.production.entity.ProductionRequestStatus;
import mrp.production.entity.ProductionStatus;
import mrp.production.repository.MaterialIssueRequestItemRepository;
import mrp.production.repository.MaterialIssueRequestRepository;
import mrp.production.repository.ProductionRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dịch vụ xử lý vòng đời Yêu cầu sản xuất (ProductionRequest) và Bảng nhu cầu NVL (Requirements).
 */
@Service
public class ProductionRequestService {

    private static final Logger log = LoggerFactory.getLogger(ProductionRequestService.class);

    private final ProductionRequestRepository prRepo;
    private final MaterialIssueRequestRepository mirRepo;
    private final MaterialIssueRequestItemRepository mirItemRepo;
    private final ProductionRepository prodRepo;
    private final BomRepository bomRepo;
    private final BomItemRepository bomItemRepo;
    private final ProductRepository productRepo;
    private final MaterialRepository materialRepo;
    private final StaffRepository staffRepo;
    private final UserRepository userRepo;
    private final WarehouseManagerRepository wmRepo;
    private final CatalogService catalogService;
    private final StockLedger stockLedger;
    private final MrpPlanService mrpPlanService;
    private final MaterialIssueService materialIssueService;
    private final JdbcTemplate jdbc;

    public ProductionRequestService(
            ProductionRequestRepository prRepo,
            MaterialIssueRequestRepository mirRepo,
            MaterialIssueRequestItemRepository mirItemRepo,
            ProductionRepository prodRepo,
            BomRepository bomRepo,
            BomItemRepository bomItemRepo,
            ProductRepository productRepo,
            MaterialRepository materialRepo,
            StaffRepository staffRepo,
            UserRepository userRepo,
            WarehouseManagerRepository wmRepo,
            CatalogService catalogService,
            StockLedger stockLedger,
            MrpPlanService mrpPlanService,
            MaterialIssueService materialIssueService,
            JdbcTemplate jdbc) {
        this.prRepo = prRepo;
        this.mirRepo = mirRepo;
        this.mirItemRepo = mirItemRepo;
        this.prodRepo = prodRepo;
        this.bomRepo = bomRepo;
        this.bomItemRepo = bomItemRepo;
        this.productRepo = productRepo;
        this.materialRepo = materialRepo;
        this.staffRepo = staffRepo;
        this.userRepo = userRepo;
        this.wmRepo = wmRepo;
        this.catalogService = catalogService;
        this.stockLedger = stockLedger;
        this.mrpPlanService = mrpPlanService;
        this.materialIssueService = materialIssueService;
        this.jdbc = jdbc;
    }

    @Transactional
    public ProductionViews.ProductionRequestDetailView createProductionRequest(
            ProductionRequests.CreateProductionRequest req, CurrentUser actor) {

        catalogService.requireActiveProduct(req.productId());

        Staff staff = staffRepo.findById(req.assignedStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên #" + req.assignedStaffId()));
        User staffUser = userRepo.findById(staff.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản người dùng"));
        if (!staffUser.isActive()) {
            throw new BusinessException("Nhân viên được giao đã bị vô hiệu hóa");
        }

        Bom bom = bomRepo.findActiveByProductId(req.productId())
                .orElseThrow(() -> new BusinessException("Sản phẩm #" + req.productId() + " không có BOM đang hoạt động"));
        if (!bomItemRepo.existsByBomId(bom.getId())) {
            throw new BusinessException("BOM #" + bom.getId() + " chưa có nguyên vật liệu");
        }

        ProductionRequest pr = new ProductionRequest(
            req.productId(),
            bom.getId(),
            req.quantity(),
            actor.profileId(),
            req.assignedStaffId(),
            req.note()
        );
        pr = prRepo.saveAndFlush(pr);
        log.info("Created production request #{} for product #{} (BOM #{}, quantity {}) assigned to staff #{}",
                pr.getId(), req.productId(), bom.getId(), req.quantity(), req.assignedStaffId());

        return detailProductionRequest(pr.getId(), actor);
    }

    public Page<ProductionViews.ProductionRequestView> listProductionRequests(
            Long productId, ProductionRequestStatus status, Long assignedTo,
            int page, int size, String sort, String direction, CurrentUser actor, boolean isStaff) {

        PageRequest pageRequest = ProductionQuery.page(page, size, sort, direction, ProductionQuery.PR_SORTS);
        final Long effectiveAssignedTo = isStaff ? actor.profileId() : assignedTo;

        Specification<ProductionRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (productId != null) {
                predicates.add(cb.equal(root.get("productId"), productId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (effectiveAssignedTo != null) {
                predicates.add(cb.equal(root.get("assignedTo"), effectiveAssignedTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ProductionRequest> prPage = prRepo.findAll(spec, pageRequest);
        if (prPage.isEmpty()) {
            return prPage.map(p -> null);
        }

        Set<Long> productIds = prPage.getContent().stream().map(ProductionRequest::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = productRepo.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        Set<Long> bomIds = prPage.getContent().stream().map(ProductionRequest::getBomId).collect(Collectors.toSet());
        Map<Long, Bom> bomMap = bomRepo.findAllById(bomIds).stream()
                .collect(Collectors.toMap(Bom::getId, b -> b));

        Set<Long> wmIds = prPage.getContent().stream().map(ProductionRequest::getWarehouseManagerId).collect(Collectors.toSet());
        Map<Long, WarehouseManager> wmMap = wmRepo.findAllById(wmIds).stream()
                .collect(Collectors.toMap(WarehouseManager::getId, w -> w));

        Set<Long> staffIds = prPage.getContent().stream().map(ProductionRequest::getAssignedTo).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, Staff> staffMap = staffRepo.findAllById(staffIds).stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        return prPage.map(pr -> {
            Product p = productMap.get(pr.getProductId());
            Bom b = bomMap.get(pr.getBomId());
            WarehouseManager wm = wmMap.get(pr.getWarehouseManagerId());
            Staff s = staffMap.get(pr.getAssignedTo());

            int completedQty = prodRepo.findByProductionRequestIdAndStatus(pr.getId(), ProductionStatus.COMPLETED)
                    .stream().mapToInt(Production::getQuantity).sum();

            return new ProductionViews.ProductionRequestView(
                pr.getId(),
                pr.getProductId(),
                p != null ? p.getName() : null,
                p != null ? p.getUnit() : null,
                pr.getBomId(),
                b != null ? b.getVersion() : null,
                pr.getQuantity(),
                completedQty,
                pr.getWarehouseManagerId(),
                wm != null ? wm.getFullName() : null,
                pr.getAssignedTo(),
                s != null ? s.getFullName() : null,
                pr.getStatus().name(),
                pr.getCreatedAt(),
                pr.getNote()
            );
        });
    }

    public ProductionViews.ProductionRequestDetailView detailProductionRequest(Long id, CurrentUser actor) {
        ProductionRequest pr = prRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu sản xuất #" + id));

        // STAFF chỉ được xem PR được giao cho mình (D11: 404)
        if (actor != null && !actor.profileId().equals(pr.getWarehouseManagerId()) && pr.getAssignedTo() != null && !pr.getAssignedTo().equals(actor.profileId())) {
            User user = userRepo.findById(actor.userId()).orElse(null);
            if (user != null && user.getRole() == UserRole.STAFF) {
                throw new ResourceNotFoundException("Không tìm thấy yêu cầu sản xuất #" + id);
            }
        }

        Product p = productRepo.findById(pr.getProductId()).orElse(null);
        Bom b = bomRepo.findById(pr.getBomId()).orElse(null);
        WarehouseManager wm = wmRepo.findById(pr.getWarehouseManagerId()).orElse(null);
        Staff s = pr.getAssignedTo() != null ? staffRepo.findById(pr.getAssignedTo()).orElse(null) : null;

        List<Production> productions = prodRepo.findByProductionRequestId(pr.getId());
        int completedQty = productions.stream().filter(prod -> prod.getStatus() == ProductionStatus.COMPLETED)
                .mapToInt(Production::getQuantity).sum();
        int inProgressQty = productions.stream().filter(prod -> prod.getStatus() == ProductionStatus.IN_PROGRESS)
                .mapToInt(Production::getQuantity).sum();

        List<ProductionViews.ProductionExecutionView> prodViews = productions.stream().map(prod ->
            new ProductionViews.ProductionExecutionView(
                prod.getId(),
                prod.getProductionRequestId(),
                prod.getQuantity(),
                prod.getProducedBy(),
                s != null ? s.getFullName() : null,
                prod.getStatus().name(),
                prod.getStartedAt(),
                prod.getCompletedAt(),
                prod.getNote()
            )
        ).toList();

        List<MaterialIssueRequest> mirs = mirRepo.findByProductionRequestId(pr.getId());
        List<ProductionViews.MaterialIssueRequestView> mirViews = mirs.stream().map(mir -> {
            long count = mirItemRepo.findByRequestId(mir.getId()).size();
            return new ProductionViews.MaterialIssueRequestView(
                mir.getId(),
                mir.getProductionRequestId(),
                mir.getRequestedBy(),
                s != null ? s.getFullName() : null,
                mir.getReviewedBy(),
                null,
                mir.getStatus().name(),
                mir.getRequestedAt(),
                mir.getReviewedAt(),
                mir.getNote(),
                (int) count
            );
        }).toList();

        return new ProductionViews.ProductionRequestDetailView(
            pr.getId(),
            pr.getProductId(),
            p != null ? p.getName() : null,
            p != null ? p.getUnit() : null,
            pr.getBomId(),
            b != null ? b.getVersion() : null,
            pr.getQuantity(),
            completedQty,
            inProgressQty,
            pr.getWarehouseManagerId(),
            wm != null ? wm.getFullName() : null,
            pr.getAssignedTo(),
            s != null ? s.getFullName() : null,
            pr.getStatus().name(),
            pr.getCreatedAt(),
            pr.getNote(),
            prodViews,
            mirViews
        );
    }

    @Transactional
    public ProductionViews.ProductionRequestDetailView cancelProductionRequest(Long id, CurrentUser actor) {
        ProductionRequest pr = prRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu sản xuất #" + id));

        if (pr.getStatus() == ProductionRequestStatus.COMPLETED || pr.getStatus() == ProductionRequestStatus.CANCELLED) {
            throw new BusinessException("Yêu cầu sản xuất ở trạng thái " + pr.getStatus() + " không thể hủy");
        }

        // Quyết định D8: Chỉ khi chưa có phiếu xuất NVL APPROVED và chưa có production không-CANCELLED
        boolean hasApprovedMir = mirRepo.existsByProductionRequestIdAndStatus(id, MaterialIssueRequestStatus.APPROVED);
        if (hasApprovedMir) {
            throw new BusinessException("Không thể hủy yêu cầu sản xuất đã có phiếu xuất NVL được duyệt");
        }

        List<Production> activeProds = prodRepo.findByProductionRequestIdAndStatusNot(id, ProductionStatus.CANCELLED);
        if (!activeProds.isEmpty()) {
            throw new BusinessException("Không thể hủy yêu cầu sản xuất đã phát sinh đợt sản xuất");
        }

        pr.setStatus(ProductionRequestStatus.CANCELLED);
        prRepo.saveAndFlush(pr);
        log.info("Cancelled production request #{} by user #{}", id, actor.userId());

        return detailProductionRequest(id, actor);
    }

    public ProductionViews.RequirementView getRequirements(Long id, CurrentUser actor) {
        ProductionRequest pr = prRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu sản xuất #" + id));

        // STAFF chỉ xem được nếu được giao (D11: 404)
        if (actor != null && pr.getAssignedTo() != null && !pr.getAssignedTo().equals(actor.profileId())) {
            User user = userRepo.findById(actor.userId()).orElse(null);
            if (user != null && user.getRole() == UserRole.STAFF) {
                throw new ResourceNotFoundException("Không tìm thấy yêu cầu sản xuất #" + id);
            }
        }

        Product product = productRepo.findById(pr.getProductId()).orElse(null);
        List<BomItem> bomItems = bomItemRepo.findByBomIdOrderByMaterialIdAsc(pr.getBomId());

        // Danh sách các PR mở khác để tính committedOther
        List<ProductionRequest> otherOpenPrs = prRepo.findByStatusIn(
                List.of(ProductionRequestStatus.PENDING, ProductionRequestStatus.IN_PROGRESS))
                .stream().filter(other -> !other.getId().equals(pr.getId())).toList();

        Set<Long> matIds = bomItems.stream().map(BomItem::getMaterialId).collect(Collectors.toSet());
        Map<Long, Material> matMap = materialRepo.findAllById(matIds).stream()
                .collect(Collectors.toMap(Material::getId, m -> m));

        List<ProductionViews.RequirementItemView> itemViews = new ArrayList<>();
        Map<Long, BigDecimal> committedOtherMap = mrpPlanService.calculateCommittedBatch(otherOpenPrs, matIds);

        for (BomItem bi : bomItems) {
            Material m = matMap.get(bi.getMaterialId());
            String code = m != null ? m.getCode() : "";
            String name = m != null ? m.getName() : "";
            String unit = m != null ? m.getUnit() : "";

            BigDecimal totalReq = bi.getQuantity().multiply(BigDecimal.valueOf(pr.getQuantity())).setScale(3, RoundingMode.HALF_UP);
            BigDecimal approvedQty = materialIssueService.getApprovedIssuedQuantity(pr.getId(), bi.getMaterialId());

            BigDecimal pendingQty = jdbc.queryForObject("""
                SELECT COALESCE(SUM(miri.quantity), 0)
                  FROM material_issue_request_items miri
                  JOIN material_issue_requests mir ON mir.id = miri.request_id
                 WHERE mir.production_request_id = ?
                   AND miri.material_id = ?
                   AND mir.status = 'PENDING'
                """, BigDecimal.class, pr.getId(), bi.getMaterialId());
            if (pendingQty == null) pendingQty = BigDecimal.ZERO;

            BigDecimal remainingAllowed = totalReq.subtract(approvedQty.add(pendingQty)).max(BigDecimal.ZERO);
            BigDecimal onHand = stockLedger.materialBalance(bi.getMaterialId());
            BigDecimal committedOther = committedOtherMap.getOrDefault(bi.getMaterialId(), BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));

            BigDecimal remainingToIssue = totalReq.subtract(approvedQty).max(BigDecimal.ZERO);
            BigDecimal usableOther = onHand.subtract(committedOther).max(BigDecimal.ZERO);
            BigDecimal shortage = remainingToIssue.subtract(usableOther).max(BigDecimal.ZERO);

            itemViews.add(new ProductionViews.RequirementItemView(
                bi.getMaterialId(),
                code,
                name,
                unit,
                bi.getQuantity(),
                totalReq,
                approvedQty,
                pendingQty,
                remainingAllowed,
                onHand,
                committedOther,
                shortage
            ));
        }

        return new ProductionViews.RequirementView(
            pr.getId(),
            pr.getProductId(),
            product != null ? product.getName() : null,
            pr.getQuantity(),
            itemViews
        );
    }
}
