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
import mrp.inventory.entity.MovementType;
import mrp.inventory.service.StockLedger;
import mrp.production.dto.ProductionQuery;
import mrp.production.dto.ProductionRequests;
import mrp.production.dto.ProductionViews;
import mrp.production.entity.MaterialIssueRequest;
import mrp.production.entity.MaterialIssueRequestItem;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductionService {

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
    private final JdbcTemplate jdbc;

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
        this.jdbc = jdbc;
    }

    // =========================================================================
    // 1. KẾ HOẠCH MRP (Quyết định D2)
    // =========================================================================

    public MrpCalculator.PlanResult getPlan(Long productId, Integer quantity) {
        if (productId == null) throw new BusinessException("productId không được để trống");
        if (quantity == null || quantity <= 0) throw new BusinessException("quantity phải lớn hơn 0");

        // Kiểm tra sản phẩm tồn tại
        Product product = catalogService.product(productId);

        // Kiểm tra BOM active
        Bom bom = bomRepo.findActiveByProductId(productId)
                .orElseThrow(() -> new BusinessException("Sản phẩm #" + productId + " không có BOM đang hoạt động"));
        List<BomItem> bomItems = bomItemRepo.findByBomIdOrderByMaterialIdAsc(bom.getId());
        if (bomItems.isEmpty()) {
            throw new BusinessException("BOM #" + bom.getId() + " chưa có nguyên vật liệu");
        }

        // Tồn thành phẩm hiện tại
        BigDecimal fgOnHand = stockLedger.productBalance(productId);

        // fgReserved: tổng số lượng phiếu xuất thành phẩm PENDING
        BigDecimal fgReserved = jdbc.queryForObject("""
            SELECT COALESCE(SUM(i.quantity), 0)
              FROM finished_goods_issue_request_items i
              JOIN finished_goods_issue_requests r ON r.id = i.request_id
             WHERE r.status = 'PENDING' AND i.product_id = ?
            """, BigDecimal.class, productId);
        if (fgReserved == null) fgReserved = BigDecimal.ZERO;

        // fgIncoming: tổng (số lượng yêu cầu - đã hoàn thành) của các PR đang mở (PENDING / IN_PROGRESS) của sản phẩm
        List<ProductionRequest> openPrsForProduct = prRepo.findByProductIdAndStatusIn(
                productId, List.of(ProductionRequestStatus.PENDING, ProductionRequestStatus.IN_PROGRESS));
        int incomingTotal = 0;
        for (ProductionRequest pr : openPrsForProduct) {
            int completedQty = prodRepo.findByProductionRequestIdAndStatus(pr.getId(), ProductionStatus.COMPLETED)
                    .stream().mapToInt(Production::getQuantity).sum();
            incomingTotal += Math.max(0, pr.getQuantity() - completedQty);
        }
        BigDecimal fgIncoming = BigDecimal.valueOf(incomingTotal);

        // Danh sách các PR đang mở trên toàn hệ thống (để tính committed của NVL)
        List<ProductionRequest> allOpenPrs = prRepo.findByStatusIn(
                List.of(ProductionRequestStatus.PENDING, ProductionRequestStatus.IN_PROGRESS));

        List<MrpCalculator.MaterialDemandInput> materialInputs = new ArrayList<>();
        Set<Long> matIds = bomItems.stream().map(BomItem::getMaterialId).collect(Collectors.toSet());
        Map<Long, Material> matMap = materialRepo.findAllById(matIds).stream()
                .collect(Collectors.toMap(Material::getId, m -> m));

        for (BomItem item : bomItems) {
            Material m = matMap.get(item.getMaterialId());
            String code = m != null ? m.getCode() : "";
            String name = m != null ? m.getName() : "";
            String unit = m != null ? m.getUnit() : "";

            BigDecimal onHand = stockLedger.materialBalance(item.getMaterialId());

            // committed: tổng max(0, BOM_item_qty * pr.qty - đã xuất APPROVED) của các PR đang mở
            BigDecimal committed = calculateCommitted(allOpenPrs, item.getMaterialId());

            materialInputs.add(new MrpCalculator.MaterialDemandInput(
                item.getMaterialId(),
                code,
                name,
                unit,
                item.getQuantity(),
                onHand,
                committed
            ));
        }

        return MrpCalculator.calculate(productId, quantity, fgOnHand, fgReserved, fgIncoming, materialInputs);
    }

    private BigDecimal calculateCommitted(List<ProductionRequest> openPrs, Long materialId) {
        BigDecimal totalCommitted = BigDecimal.ZERO;
        for (ProductionRequest pr : openPrs) {
            // Lấy BOM item tương ứng của PR đó
            List<BomItem> prBomItems = bomItemRepo.findByBomIdOrderByMaterialIdAsc(pr.getBomId());
            for (BomItem bi : prBomItems) {
                if (bi.getMaterialId().equals(materialId)) {
                    BigDecimal required = bi.getQuantity().multiply(BigDecimal.valueOf(pr.getQuantity()));
                    BigDecimal approvedIssued = getApprovedIssuedQuantity(pr.getId(), materialId);
                    BigDecimal remaining = required.subtract(approvedIssued);
                    if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                        totalCommitted = totalCommitted.add(remaining);
                    }
                }
            }
        }
        return totalCommitted.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal getApprovedIssuedQuantity(Long prId, Long materialId) {
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

    // =========================================================================
    // 2. YÊU CẦU SẢN XUẤT (ProductionRequest)
    // =========================================================================

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

        // Batch lookups to prevent N+1
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

        // STAFF chỉ được xem PR được giao cho mình (D11: 404 để không lộ tồn tại)
        if (actor != null && !actor.profileId().equals(pr.getWarehouseManagerId()) && pr.getAssignedTo() != null && !pr.getAssignedTo().equals(actor.profileId())) {
            // Kiểm tra xem user có phải STAFF không
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

        for (BomItem bi : bomItems) {
            Material m = matMap.get(bi.getMaterialId());
            String code = m != null ? m.getCode() : "";
            String name = m != null ? m.getName() : "";
            String unit = m != null ? m.getUnit() : "";

            BigDecimal totalReq = bi.getQuantity().multiply(BigDecimal.valueOf(pr.getQuantity())).setScale(3, RoundingMode.HALF_UP);
            BigDecimal approvedQty = getApprovedIssuedQuantity(pr.getId(), bi.getMaterialId());

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
            BigDecimal committedOther = calculateCommitted(otherOpenPrs, bi.getMaterialId());

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

    // =========================================================================
    // 3. PHIẾU XUẤT NGUYÊN VẬT LIỆU (MaterialIssueRequest)
    // =========================================================================

    @Transactional
    public ProductionViews.MaterialIssueRequestDetailView createMaterialIssueRequest(
            ProductionRequests.CreateMaterialIssueRequest req, CurrentUser actor) {

        ProductionRequest pr = prRepo.findByIdForUpdate(req.productionRequestId())
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

    private ProductionViews.MaterialIssueRequestDetailView detailMaterialIssueRequest(
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

    // =========================================================================
    // 4. SẢN XUẤT (Production)
    // =========================================================================

    @Transactional
    public ProductionViews.ProductionExecutionView createProduction(
            ProductionRequests.CreateProductionExecutionRequest req, CurrentUser actor) {

        ProductionRequest pr = prRepo.findByIdForUpdate(req.productionRequestId())
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
            BigDecimal approvedIssued = getApprovedIssuedQuantity(pr.getId(), bi.getMaterialId());
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