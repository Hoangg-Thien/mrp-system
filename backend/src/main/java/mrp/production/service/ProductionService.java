package mrp.production.service;

import mrp.bom.repository.BomItemRepository;
import mrp.bom.repository.BomRepository;
import mrp.catalog.repository.MaterialRepository;
import mrp.catalog.repository.ProductRepository;
import mrp.catalog.service.CatalogService;
import mrp.inventory.service.StockLedger;
import mrp.production.dto.ProductionRequests;
import mrp.production.dto.ProductionViews;
import mrp.production.entity.MaterialIssueRequestStatus;
import mrp.production.entity.ProductionRequest;
import mrp.production.entity.ProductionRequestStatus;
import mrp.production.entity.ProductionStatus;
import mrp.production.repository.MaterialIssueRequestItemRepository;
import mrp.production.repository.MaterialIssueRequestRepository;
import mrp.production.repository.ProductionRepository;
import mrp.production.repository.ProductionRequestRepository;
import mrp.security.CurrentUser;
import mrp.user.repository.StaffRepository;
import mrp.user.repository.UserRepository;
import mrp.user.repository.WarehouseManagerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Facade điều phối các dịch vụ nghiệp vụ trong phân hệ sản xuất (MRP).
 * Các trách nhiệm chi tiết được phân rã thành:
 * <ul>
 *   <li>{@link MrpPlanService}: Tính toán MRP Plan và số lượng cam kết (committed)</li>
 *   <li>{@link ProductionRequestService}: Vòng đời yêu cầu sản xuất và bảng nhu cầu NVL</li>
 *   <li>{@link MaterialIssueService}: Vòng đời phiếu xuất NVL và xuất kho</li>
 *   <li>{@link ProductionExecutionService}: Đợt sản xuất, hoàn thành và nhập kho thành phẩm</li>
 * </ul>
 */
@Service
public class ProductionService {

    private final MrpPlanService mrpPlanService;
    private final ProductionRequestService prService;
    private final MaterialIssueService mirService;
    private final ProductionExecutionService prodService;

    @Autowired
    public ProductionService(
            MrpPlanService mrpPlanService,
            ProductionRequestService prService,
            MaterialIssueService mirService,
            ProductionExecutionService prodService) {
        this.mrpPlanService = mrpPlanService;
        this.prService = prService;
        this.mirService = mirService;
        this.prodService = prodService;
    }

    /**
     * Constructor tương thích ngược cho unit test và kiểm thử khởi tạo trực tiếp.
     */
    public ProductionService(
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
            JdbcTemplate jdbc) {
        this.mrpPlanService = new MrpPlanService(
                catalogService, bomRepo, bomItemRepo, stockLedger, prRepo, prodRepo, materialRepo, jdbc);
        this.mirService = new MaterialIssueService(
                prRepo, mirRepo, mirItemRepo, bomItemRepo, materialRepo, staffRepo, userRepo, wmRepo, stockLedger, jdbc);
        this.prodService = new ProductionExecutionService(
                prodRepo, prRepo, bomItemRepo, staffRepo, userRepo, stockLedger, this.mirService);
        this.prService = new ProductionRequestService(
                prRepo, mirRepo, mirItemRepo, prodRepo, bomRepo, bomItemRepo, productRepo, materialRepo,
                staffRepo, userRepo, wmRepo, catalogService, stockLedger, this.mrpPlanService, this.mirService, jdbc);
    }

    // =========================================================================
    // 1. KẾ HOẠCH MRP
    // =========================================================================

    public MrpCalculator.PlanResult getPlan(Long productId, Integer quantity) {
        return mrpPlanService.getPlan(productId, quantity);
    }

    public Map<Long, BigDecimal> calculateCommittedBatch(Collection<ProductionRequest> openPrs, Collection<Long> materialIds) {
        return mrpPlanService.calculateCommittedBatch(openPrs, materialIds);
    }

    public BigDecimal calculateCommitted(List<ProductionRequest> openPrs, Long materialId) {
        return mrpPlanService.calculateCommitted(openPrs, materialId);
    }

    // =========================================================================
    // 2. YÊU CẦU SẢN XUẤT (ProductionRequest)
    // =========================================================================

    @Transactional
    public ProductionViews.ProductionRequestDetailView createProductionRequest(
            ProductionRequests.CreateProductionRequest req, CurrentUser actor) {
        return prService.createProductionRequest(req, actor);
    }

    public Page<ProductionViews.ProductionRequestView> listProductionRequests(
            Long productId, ProductionRequestStatus status, Long assignedTo,
            int page, int size, String sort, String direction, CurrentUser actor, boolean isStaff) {
        return prService.listProductionRequests(productId, status, assignedTo, page, size, sort, direction, actor, isStaff);
    }

    public ProductionViews.ProductionRequestDetailView detailProductionRequest(Long id, CurrentUser actor) {
        return prService.detailProductionRequest(id, actor);
    }

    @Transactional
    public ProductionViews.ProductionRequestDetailView cancelProductionRequest(Long id, CurrentUser actor) {
        return prService.cancelProductionRequest(id, actor);
    }

    public ProductionViews.RequirementView getRequirements(Long id, CurrentUser actor) {
        return prService.getRequirements(id, actor);
    }

    // =========================================================================
    // 3. PHIẾU XUẤT NGUYÊN VẬT LIỆU (MaterialIssueRequest)
    // =========================================================================

    @Transactional
    public ProductionViews.MaterialIssueRequestDetailView createMaterialIssueRequest(
            ProductionRequests.CreateMaterialIssueRequest req, CurrentUser actor) {
        return mirService.createMaterialIssueRequest(req, actor);
    }

    public Page<ProductionViews.MaterialIssueRequestView> listMaterialIssueRequests(
            Long productionRequestId, MaterialIssueRequestStatus status, Long requestedBy,
            int page, int size, String sort, String direction, CurrentUser actor, boolean isStaff) {
        return mirService.listMaterialIssueRequests(productionRequestId, status, requestedBy, page, size, sort, direction, actor, isStaff);
    }

    public ProductionViews.MaterialIssueRequestDetailView detailMaterialIssueRequest(Long id, CurrentUser actor) {
        return mirService.detailMaterialIssueRequest(id, actor);
    }

    @Transactional
    public ProductionViews.MaterialIssueRequestDetailView approveMaterialIssueRequest(Long id, CurrentUser actor) {
        return mirService.approveMaterialIssueRequest(id, actor);
    }

    @Transactional
    public ProductionViews.MaterialIssueRequestDetailView rejectMaterialIssueRequest(
            Long id, ProductionRequests.RejectRequest req, CurrentUser actor) {
        return mirService.rejectMaterialIssueRequest(id, req, actor);
    }

    @Transactional
    public ProductionViews.MaterialIssueRequestDetailView cancelMaterialIssueRequest(Long id, CurrentUser actor) {
        return mirService.cancelMaterialIssueRequest(id, actor);
    }

    // =========================================================================
    // 4. SẢN XUẤT (Production)
    // =========================================================================

    @Transactional
    public ProductionViews.ProductionExecutionView createProduction(
            ProductionRequests.CreateProductionExecutionRequest req, CurrentUser actor) {
        return prodService.createProduction(req, actor);
    }

    public Page<ProductionViews.ProductionExecutionView> listProductions(
            Long productionRequestId, ProductionStatus status,
            int page, int size, String sort, String direction, CurrentUser actor, boolean isStaff) {
        return prodService.listProductions(productionRequestId, status, page, size, sort, direction, actor, isStaff);
    }

    public ProductionViews.ProductionExecutionView detailProduction(Long id, CurrentUser actor) {
        return prodService.detailProduction(id, actor);
    }

    @Transactional
    public ProductionViews.ProductionExecutionView completeProduction(Long id, CurrentUser actor) {
        return prodService.completeProduction(id, actor);
    }

    @Transactional
    public ProductionViews.ProductionExecutionView cancelProduction(Long id, CurrentUser actor) {
        return prodService.cancelProduction(id, actor);
    }
}