package mrp.production.service;

import mrp.bom.entity.BomItem;
import mrp.bom.repository.BomItemRepository;
import mrp.common.exception.BusinessException;
import mrp.common.exception.ResourceNotFoundException;
import mrp.inventory.entity.MovementType;
import mrp.inventory.service.StockLedger;
import mrp.production.dto.ProductionQuery;
import mrp.production.dto.ProductionRequests;
import mrp.production.dto.ProductionViews;
import mrp.production.entity.Production;
import mrp.production.entity.ProductionRequest;
import mrp.production.entity.ProductionRequestStatus;
import mrp.production.entity.ProductionStatus;
import mrp.production.repository.ProductionRepository;
import mrp.production.repository.ProductionRequestRepository;
import mrp.security.CurrentUser;
import mrp.user.entity.Staff;
import mrp.user.entity.User;
import mrp.user.entity.UserRole;
import mrp.user.repository.StaffRepository;
import mrp.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dịch vụ xử lý vòng đời Đợt sản xuất (Production Execution).
 */
@Service
public class ProductionExecutionService {

    private final ProductionRepository prodRepo;
    private final ProductionRequestRepository prRepo;
    private final BomItemRepository bomItemRepo;
    private final StaffRepository staffRepo;
    private final UserRepository userRepo;
    private final StockLedger stockLedger;
    private final MaterialIssueService materialIssueService;

    public ProductionExecutionService(
            ProductionRepository prodRepo,
            ProductionRequestRepository prRepo,
            BomItemRepository bomItemRepo,
            StaffRepository staffRepo,
            UserRepository userRepo,
            StockLedger stockLedger,
            MaterialIssueService materialIssueService) {
        this.prodRepo = prodRepo;
        this.prRepo = prRepo;
        this.bomItemRepo = bomItemRepo;
        this.staffRepo = staffRepo;
        this.userRepo = userRepo;
        this.stockLedger = stockLedger;
        this.materialIssueService = materialIssueService;
    }

    @Transactional
    public ProductionViews.ProductionExecutionView createProduction(
            ProductionRequests.CreateProductionExecutionRequest req, CurrentUser actor) {

        ProductionRequest pr = prRepo.findById(req.productionRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu sản xuất #" + req.productionRequestId()));

        // Phải đúng nhân viên được giao (D11 / trigger trg_prod_actor)
        if (pr.getAssignedTo() == null || !pr.getAssignedTo().equals(actor.profileId())) {
            throw new ResourceNotFoundException("Không tìm thấy yêu cầu sản xuất #" + req.productionRequestId());
        }

        if (pr.getStatus() != ProductionRequestStatus.PENDING && pr.getStatus() != ProductionRequestStatus.IN_PROGRESS) {
            throw new BusinessException("Yêu cầu sản xuất #" + pr.getId() + " đang " + pr.getStatus() + ", không thể sản xuất");
        }

        // Kiểm tra tổng số lượng các đợt sản xuất không-CANCELLED <= pr.quantity
        List<Production> prods = prodRepo.findByProductionRequestIdAndStatusNot(pr.getId(), ProductionStatus.CANCELLED);
        int currentProduced = prods.stream().mapToInt(Production::getQuantity).sum();
        if (currentProduced + req.quantity() > pr.getQuantity()) {
            throw new BusinessException("Tổng số lượng sản xuất (" + (currentProduced + req.quantity())
                    + ") vượt số lượng yêu cầu (" + pr.getQuantity() + ")");
        }

        Production prod = new Production(pr.getId(), req.quantity(), actor.profileId(), req.note());
        prod = prodRepo.saveAndFlush(prod);

        // Quyết định D4: Chuyển PR sang IN_PROGRESS khi bắt đầu đợt sản xuất đầu tiên
        if (pr.getStatus() == ProductionRequestStatus.PENDING) {
            pr.setStatus(ProductionRequestStatus.IN_PROGRESS);
            prRepo.saveAndFlush(pr);
        }

        Staff staff = staffRepo.findById(actor.profileId()).orElse(null);
        return new ProductionViews.ProductionExecutionView(
            prod.getId(),
            prod.getProductionRequestId(),
            prod.getQuantity(),
            prod.getProducedBy(),
            staff != null ? staff.getFullName() : null,
            prod.getStatus().name(),
            prod.getStartedAt(),
            prod.getCompletedAt(),
            prod.getNote()
        );
    }

    public Page<ProductionViews.ProductionExecutionView> listProductions(
            Long productionRequestId, ProductionStatus status,
            int page, int size, String sort, String direction, CurrentUser actor, boolean isStaff) {

        PageRequest pageRequest = ProductionQuery.page(page, size, sort, direction, ProductionQuery.PROD_SORTS);

        Specification<Production> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (productionRequestId != null) {
                predicates.add(cb.equal(root.get("productionRequestId"), productionRequestId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (isStaff && actor != null) {
                predicates.add(cb.equal(root.get("producedBy"), actor.profileId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Production> prodPage = prodRepo.findAll(spec, pageRequest);
        if (prodPage.isEmpty()) {
            return prodPage.map(p -> null);
        }

        Set<Long> staffIds = prodPage.getContent().stream().map(Production::getProducedBy).collect(Collectors.toSet());
        Map<Long, Staff> staffMap = staffRepo.findAllById(staffIds).stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        return prodPage.map(prod -> {
            Staff s = staffMap.get(prod.getProducedBy());
            return new ProductionViews.ProductionExecutionView(
                prod.getId(),
                prod.getProductionRequestId(),
                prod.getQuantity(),
                prod.getProducedBy(),
                s != null ? s.getFullName() : null,
                prod.getStatus().name(),
                prod.getStartedAt(),
                prod.getCompletedAt(),
                prod.getNote()
            );
        });
    }

    public ProductionViews.ProductionExecutionView detailProduction(Long id, CurrentUser actor) {
        Production prod = prodRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đợt sản xuất #" + id));

        if (actor != null && !prod.getProducedBy().equals(actor.profileId())) {
            User user = userRepo.findById(actor.userId()).orElse(null);
            if (user != null && user.getRole() == UserRole.STAFF) {
                throw new ResourceNotFoundException("Không tìm thấy đợt sản xuất #" + id);
            }
        }

        Staff s = staffRepo.findById(prod.getProducedBy()).orElse(null);
        return new ProductionViews.ProductionExecutionView(
            prod.getId(),
            prod.getProductionRequestId(),
            prod.getQuantity(),
            prod.getProducedBy(),
            s != null ? s.getFullName() : null,
            prod.getStatus().name(),
            prod.getStartedAt(),
            prod.getCompletedAt(),
            prod.getNote()
        );
    }

    @Transactional
    public ProductionViews.ProductionExecutionView completeProduction(Long id, CurrentUser actor) {
        Production prod = prodRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đợt sản xuất #" + id));

        if (!prod.getProducedBy().equals(actor.profileId())) {
            throw new ResourceNotFoundException("Không tìm thấy đợt sản xuất #" + id);
        }

        if (prod.getStatus() != ProductionStatus.IN_PROGRESS) {
            throw new BusinessException("Đợt sản xuất ở trạng thái " + prod.getStatus() + " không thể hoàn thành");
        }

        ProductionRequest pr = prRepo.findByIdForUpdate(prod.getProductionRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu sản xuất #" + prod.getProductionRequestId()));

        // Tổng đã hoàn thành trước đợt này
        int previouslyDone = prodRepo.findByProductionRequestIdAndStatus(pr.getId(), ProductionStatus.COMPLETED)
                .stream().mapToInt(Production::getQuantity).sum();
        int totalDoneAfterThis = previouslyDone + prod.getQuantity();

        // Kiểm tra nguyên vật liệu đã xuất kho đủ theo BOM (mục 5.2.3 & trigger DB)
        List<BomItem> bomItems = bomItemRepo.findByBomIdOrderByMaterialIdAsc(pr.getBomId());
        for (BomItem bi : bomItems) {
            BigDecimal needed = bi.getQuantity().multiply(BigDecimal.valueOf(totalDoneAfterThis)).setScale(3, RoundingMode.HALF_UP);
            BigDecimal approvedIssued = materialIssueService.getApprovedIssuedQuantity(pr.getId(), bi.getMaterialId());
            if (approvedIssued.compareTo(needed) < 0) {
                throw new BusinessException("NVL đã xuất kho chưa đủ theo BOM để hoàn thành số lượng này (NVL #"
                        + bi.getMaterialId() + ": cần " + needed + ", đã xuất " + approvedIssued + ")");
            }
        }

        prod.setStatus(ProductionStatus.COMPLETED);
        prod.setCompletedAt(LocalDateTime.now());
        prodRepo.saveAndFlush(prod);

        // Ghi nhận nhập kho thành phẩm từ sản xuất
        stockLedger.post(
            MovementType.IN,
            null,
            pr.getProductId(),
            BigDecimal.valueOf(prod.getQuantity()),
            null,
            null,
            prod.getId(),
            null,
            actor.userId(),
            "Nhập kho thành phẩm từ sản xuất #" + prod.getId()
        );

        // Quyết định D4: Nếu tổng COMPLETED = số lượng yêu cầu -> production_request COMPLETED
        if (totalDoneAfterThis >= pr.getQuantity()) {
            pr.setStatus(ProductionRequestStatus.COMPLETED);
            prRepo.saveAndFlush(pr);
        }

        Staff s = staffRepo.findById(actor.profileId()).orElse(null);
        return new ProductionViews.ProductionExecutionView(
            prod.getId(),
            prod.getProductionRequestId(),
            prod.getQuantity(),
            prod.getProducedBy(),
            s != null ? s.getFullName() : null,
            prod.getStatus().name(),
            prod.getStartedAt(),
            prod.getCompletedAt(),
            prod.getNote()
        );
    }

    @Transactional
    public ProductionViews.ProductionExecutionView cancelProduction(Long id, CurrentUser actor) {
        Production prod = prodRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đợt sản xuất #" + id));

        if (!prod.getProducedBy().equals(actor.profileId())) {
            throw new ResourceNotFoundException("Không tìm thấy đợt sản xuất #" + id);
        }

        if (prod.getStatus() != ProductionStatus.IN_PROGRESS) {
            throw new BusinessException("Chỉ được hủy đợt sản xuất khi đang IN_PROGRESS (hiện tại: " + prod.getStatus() + ")");
        }

        prod.setStatus(ProductionStatus.CANCELLED);
        prodRepo.saveAndFlush(prod);

        Staff s = staffRepo.findById(actor.profileId()).orElse(null);
        return new ProductionViews.ProductionExecutionView(
            prod.getId(),
            prod.getProductionRequestId(),
            prod.getQuantity(),
            prod.getProducedBy(),
            s != null ? s.getFullName() : null,
            prod.getStatus().name(),
            prod.getStartedAt(),
            prod.getCompletedAt(),
            prod.getNote()
        );
    }
}
