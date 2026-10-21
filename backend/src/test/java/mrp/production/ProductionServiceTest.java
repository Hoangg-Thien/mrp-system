package mrp.production;


import mrp.bom.entity.Bom;
import mrp.bom.entity.BomItem;
import mrp.bom.repository.BomItemRepository;
import mrp.bom.repository.BomRepository;
import mrp.catalog.dto.CatalogRequests;
import mrp.catalog.entity.Material;
import mrp.catalog.entity.Product;
import mrp.catalog.repository.MaterialRepository;
import mrp.catalog.repository.ProductRepository;
import mrp.catalog.service.CatalogService;
import mrp.common.exception.BusinessException;
import mrp.common.exception.ResourceNotFoundException;
import mrp.inventory.entity.MovementType;
import mrp.inventory.service.StockLedger;
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
import mrp.production.service.MrpCalculator;
import mrp.production.service.ProductionService;
import mrp.security.CurrentUser;
import mrp.user.entity.Staff;
import mrp.user.entity.User;
import mrp.user.entity.UserRole;
import mrp.user.repository.StaffRepository;
import mrp.user.repository.UserRepository;
import mrp.user.repository.WarehouseManagerRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ProductionServiceTest {

    @Mock private ProductionRequestRepository prRepo;
    @Mock private MaterialIssueRequestRepository mirRepo;
    @Mock private MaterialIssueRequestItemRepository mirItemRepo;
    @Mock private ProductionRepository prodRepo;
    @Mock private BomRepository bomRepo;
    @Mock private BomItemRepository bomItemRepo;
    @Mock private ProductRepository productRepo;
    @Mock private MaterialRepository materialRepo;
    @Mock private StaffRepository staffRepo;
    @Mock private UserRepository userRepo;
    @Mock private WarehouseManagerRepository wmRepo;
    @Mock private CatalogService catalogService;
    @Mock private StockLedger stockLedger;
    @Mock private JdbcTemplate jdbc;

    private ProductionService service;

    @BeforeEach
    void setUp() {
        service = new ProductionService(
            prRepo, mirRepo, mirItemRepo, prodRepo,
            bomRepo, bomItemRepo, productRepo, materialRepo,
            staffRepo, userRepo, wmRepo, catalogService, stockLedger, jdbc
        );
    }

    private Product createProduct(Long id, String name, String unit, boolean active) {
        try {
            Constructor<Product> c = Product.class.getDeclaredConstructor();
            c.setAccessible(true);
            Product p = c.newInstance();
            p.update(new CatalogRequests.ProductRequest(1L, name, "desc", unit, BigDecimal.valueOf(500000)));
            p.setActive(active);
            Field f = Product.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
            return p;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private Material createMaterial(Long id, String code, String name, String unit, boolean active) {
        try {
            Constructor<Material> c = Material.class.getDeclaredConstructor();
            c.setAccessible(true);
            Material m = c.newInstance();
            m.update(new CatalogRequests.MaterialRequest(code, name, unit, BigDecimal.valueOf(50000)));
            m.setActive(active);
            Field f = Material.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(m, id);
            return m;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private Bom createBom(Long id, Long productId, int version, boolean active) {
        Bom b = new Bom(productId, version, active);
        try {
            Field f = Bom.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(b, id);
        } catch (Exception ignored) { }
        return b;
    }

    private BomItem createBomItem(Long id, Long bomId, Long materialId, BigDecimal qty) {
        BomItem item = new BomItem(bomId, materialId, qty);
        try {
            Field f = BomItem.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(item, id);
        } catch (Exception ignored) { }
        return item;
    }

    private ProductionRequest createPr(Long id, Long productId, Long bomId, int qty, Long wmId, Long staffId, ProductionRequestStatus status) {
        ProductionRequest pr = new ProductionRequest(productId, bomId, qty, wmId, staffId, "note");
        pr.setStatus(status);
        try {
            Field f = ProductionRequest.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(pr, id);
        } catch (Exception ignored) { }
        return pr;
    }

    private MaterialIssueRequest createMir(Long id, Long prId, Long staffId, MaterialIssueRequestStatus status) {
        MaterialIssueRequest mir = new MaterialIssueRequest(prId, staffId, "note");
        mir.setStatus(status);
        try {
            Field f = MaterialIssueRequest.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(mir, id);
        } catch (Exception ignored) { }
        return mir;
    }

    private Production createProd(Long id, Long prId, int qty, Long staffId, ProductionStatus status) {
        Production p = new Production(prId, qty, staffId, "prod note");
        p.setStatus(status);
        try {
            Field f = Production.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
        } catch (Exception ignored) { }
        return p;
    }

    private Staff createStaff(Long id, Long userId, String name) {
        Staff s = new Staff(userId, name, "staff@test.com", "0123456789", "HCM");
        try {
            Field f = Staff.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(s, id);
        } catch (Exception ignored) { }
        return s;
    }

    private User createUser(Long id, String username, UserRole role, boolean active) {
        User u = new User(username, "hash", role);
        u.setActive(active);
        try {
            Field f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception ignored) { }
        return u;
    }

    // =========================================================================
    // MRP PLAN TESTS (MRP-01, MRP-01b)
    // =========================================================================

    @Test
    void mrp01_getPlanReturnsCalculation() {
        Long productId = 10L;
        Product product = createProduct(productId, "Bàn gỗ", "cái", true);
        when(catalogService.product(productId)).thenReturn(product);

        Bom bom = createBom(1L, productId, 1, true);
        when(bomRepo.findActiveByProductId(productId)).thenReturn(Optional.of(bom));

        BomItem matItem = createBomItem(1001L, 1L, 101L, new BigDecimal("1.000"));
        BomItem chanItem = createBomItem(1002L, 1L, 102L, new BigDecimal("4.000"));
        when(bomItemRepo.findByBomIdOrderByMaterialIdAsc(1L)).thenReturn(List.of(matItem, chanItem));

        when(stockLedger.productBalance(productId)).thenReturn(new BigDecimal("2.000"));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(productId))).thenReturn(new BigDecimal("1.000"));

        ProductionRequest openPr = createPr(100L, productId, 1L, 5, 1L, 1L, ProductionRequestStatus.IN_PROGRESS);
        when(prRepo.findByProductIdAndStatusIn(eq(productId), anyList())).thenReturn(List.of(openPr));

        Production prodDone = createProd(500L, 100L, 2, 1L, ProductionStatus.COMPLETED);
        when(prodRepo.findByProductionRequestIdAndStatus(100L, ProductionStatus.COMPLETED)).thenReturn(List.of(prodDone));

        when(prRepo.findByStatusIn(anyList())).thenReturn(List.of());

        Material matMatBan = createMaterial(101L, "MAT-01", "Mặt bàn", "tấm", true);
        Material matChanBan = createMaterial(102L, "CHAN-01", "Chân bàn", "cái", true);
        when(materialRepo.findAllById(any())).thenReturn(List.of(matMatBan, matChanBan));

        when(stockLedger.materialBalance(101L)).thenReturn(new BigDecimal("5.000"));
        when(stockLedger.materialBalance(102L)).thenReturn(new BigDecimal("30.000"));

        MrpCalculator.PlanResult plan = service.getPlan(productId, 10);

        assertNotNull(plan);
        assertEquals(new BigDecimal("4.000"), plan.available()); // 2 - 1 + (5 - 2) = 4
        assertEquals(6, plan.toProduce()); // 10 - 4 = 6
        assertEquals(2, plan.materials().size());
        assertEquals(new BigDecimal("1.000"), plan.materials().get(0).shortage()); // gross 6 - 5 = 1
        assertEquals(new BigDecimal("0.000"), plan.materials().get(1).shortage()); // gross 24 <= 30
    }

    @Test
    void mrp01b_getPlanWithoutActiveBomThrowsBusinessException() {
        Long productId = 10L;
        when(catalogService.product(productId)).thenReturn(createProduct(productId, "Bàn gỗ", "cái", true));
        when(bomRepo.findActiveByProductId(productId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getPlan(productId, 10));
        assertTrue(ex.getMessage().contains("không có BOM đang hoạt động"));
    }

    // =========================================================================
    // PRODUCTION REQUEST TESTS (MRP-02, MRP-03, MRP-04, MRP-13)
    // =========================================================================

    @Test
    void mrp02_createProductionRequestSucceeds() {
        Long productId = 10L;
        Long staffId = 5L;
        CurrentUser actor = new CurrentUser(1L, 2L, "wm01");

        when(catalogService.requireActiveProduct(productId)).thenReturn(createProduct(productId, "Bàn gỗ", "cái", true));
        Staff staff = createStaff(staffId, 50L, "Nguyễn Văn A");
        when(staffRepo.findById(staffId)).thenReturn(Optional.of(staff));
        when(userRepo.findById(50L)).thenReturn(Optional.of(createUser(50L, "staff01", UserRole.STAFF, true)));

        Bom bom = createBom(1L, productId, 1, true);
        when(bomRepo.findActiveByProductId(productId)).thenReturn(Optional.of(bom));
        when(bomItemRepo.existsByBomId(1L)).thenReturn(true);

        ProductionRequest savedPr = createPr(100L, productId, 1L, 10, 2L, staffId, ProductionRequestStatus.PENDING);
        when(prRepo.saveAndFlush(any(ProductionRequest.class))).thenReturn(savedPr);
        when(prRepo.findById(100L)).thenReturn(Optional.of(savedPr));
        when(productRepo.findById(productId)).thenReturn(Optional.of(createProduct(productId, "Bàn gỗ", "cái", true)));
        when(bomRepo.findById(1L)).thenReturn(Optional.of(bom));
        when(staffRepo.findById(staffId)).thenReturn(Optional.of(staff));

        ProductionRequests.CreateProductionRequest req = new ProductionRequests.CreateProductionRequest(productId, 10, staffId, "Gấp");
        ProductionViews.ProductionRequestDetailView view = service.createProductionRequest(req, actor);

        assertNotNull(view);
        assertEquals(100L, view.id());
        assertEquals("PENDING", view.status());
        assertEquals(10, view.quantity());
    }

    @Test
    void mrp02_createProductionRequestFailsWhenStaffInactive() {
        Long productId = 10L;
        Long staffId = 5L;
        CurrentUser actor = new CurrentUser(1L, 2L, "wm01");

        when(catalogService.requireActiveProduct(productId)).thenReturn(createProduct(productId, "Bàn gỗ", "cái", true));
        Staff staff = createStaff(staffId, 50L, "Nguyễn Văn A");
        when(staffRepo.findById(staffId)).thenReturn(Optional.of(staff));
        when(userRepo.findById(50L)).thenReturn(Optional.of(createUser(50L, "staff01", UserRole.STAFF, false)));

        ProductionRequests.CreateProductionRequest req = new ProductionRequests.CreateProductionRequest(productId, 10, staffId, "Gấp");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createProductionRequest(req, actor));
        assertTrue(ex.getMessage().contains("vô hiệu hóa"));
    }

    @Test
    void mrp03_getRequirementsReturnsBreakdown() {
        Long prId = 100L;
        Long productId = 10L;
        ProductionRequest pr = createPr(prId, productId, 1L, 10, 2L, 5L, ProductionRequestStatus.IN_PROGRESS);
        when(prRepo.findById(prId)).thenReturn(Optional.of(pr));
        when(productRepo.findById(productId)).thenReturn(Optional.of(createProduct(productId, "Bàn gỗ", "cái", true)));

        BomItem item = createBomItem(1001L, 1L, 101L, new BigDecimal("2.000"));
        when(bomItemRepo.findByBomIdOrderByMaterialIdAsc(1L)).thenReturn(List.of(item));

        Material mat = createMaterial(101L, "MAT-01", "Mặt bàn", "tấm", true);
        when(materialRepo.findAllById(any())).thenReturn(List.of(mat));

        when(prRepo.findByStatusIn(anyList())).thenReturn(List.of());
        when(stockLedger.materialBalance(101L)).thenReturn(new BigDecimal("15.000"));

        CurrentUser actor = new CurrentUser(50L, 5L, "staff01");
        ProductionViews.RequirementView reqView = service.getRequirements(prId, actor);

        assertNotNull(reqView);
        assertEquals(1, reqView.materials().size());
        ProductionViews.RequirementItemView itemReq = reqView.materials().get(0);
        assertEquals(new BigDecimal("20.000"), itemReq.totalRequired()); // 2 * 10 = 20
        assertEquals(new BigDecimal("15.000"), itemReq.onHand());
    }

    @Test
    void mrp04_staffCannotAccessOtherStaffProductionRequest() {
        Long prId = 100L;
        // PR giao cho staff 5L
        ProductionRequest pr = createPr(prId, 10L, 1L, 10, 2L, 5L, ProductionRequestStatus.PENDING);
        when(prRepo.findById(prId)).thenReturn(Optional.of(pr));

        // Actor là staff 6L
        CurrentUser staffB = new CurrentUser(60L, 6L, "staffB");
        when(userRepo.findById(60L)).thenReturn(Optional.of(createUser(60L, "staffB", UserRole.STAFF, true)));

        assertThrows(ResourceNotFoundException.class, () -> service.detailProductionRequest(prId, staffB));
    }

    @Test
    void mrp13_cancelProductionRequestAllowedWhenNoApprovedMirAndNoProds() {
        Long prId = 100L;
        ProductionRequest pr = createPr(prId, 10L, 1L, 10, 2L, 5L, ProductionRequestStatus.PENDING);
        when(prRepo.findByIdForUpdate(prId)).thenReturn(Optional.of(pr));
        when(mirRepo.existsByProductionRequestIdAndStatus(prId, MaterialIssueRequestStatus.APPROVED)).thenReturn(false);
        when(prodRepo.findByProductionRequestIdAndStatusNot(prId, ProductionStatus.CANCELLED)).thenReturn(List.of());
        when(prRepo.findById(prId)).thenReturn(Optional.of(pr));

        CurrentUser actor = new CurrentUser(1L, 2L, "wm01");
        ProductionViews.ProductionRequestDetailView res = service.cancelProductionRequest(prId, actor);

        assertEquals("CANCELLED", pr.getStatus().name());
    }

    @Test
    void mrp13b_cancelProductionRequestFailsWhenApprovedMirExists() {
        Long prId = 100L;
        ProductionRequest pr = createPr(prId, 10L, 1L, 10, 2L, 5L, ProductionRequestStatus.PENDING);
        when(prRepo.findByIdForUpdate(prId)).thenReturn(Optional.of(pr));
        when(mirRepo.existsByProductionRequestIdAndStatus(prId, MaterialIssueRequestStatus.APPROVED)).thenReturn(true);

        CurrentUser actor = new CurrentUser(1L, 2L, "wm01");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelProductionRequest(prId, actor));
        assertTrue(ex.getMessage().contains("đã có phiếu xuất NVL được duyệt"));
    }

    // =========================================================================
    // MATERIAL ISSUE REQUEST TESTS (MRP-05, MRP-06, MRP-07, MRP-08)
    // =========================================================================

    @Test
    void mrp05_createMaterialIssueRequestSucceedsWithinBom() {
        Long prId = 100L;
        Long staffId = 5L;
        ProductionRequest pr = createPr(prId, 10L, 1L, 10, 2L, staffId, ProductionRequestStatus.PENDING);
        when(prRepo.findById(prId)).thenReturn(Optional.of(pr));

        BomItem item = createBomItem(1001L, 1L, 101L, new BigDecimal("2.000")); // Quota: 2 * 10 = 20
        when(bomItemRepo.findByBomIdOrderByMaterialIdAsc(1L)).thenReturn(List.of(item));

        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(prId), eq(101L))).thenReturn(BigDecimal.ZERO);
        when(stockLedger.materialBalance(101L)).thenReturn(new BigDecimal("50.000"));

        MaterialIssueRequest savedMir = createMir(10L, prId, staffId, MaterialIssueRequestStatus.PENDING);
        when(mirRepo.saveAndFlush(any(MaterialIssueRequest.class))).thenReturn(savedMir);
        when(mirRepo.findById(10L)).thenReturn(Optional.of(savedMir));

        ProductionRequests.CreateMaterialIssueRequest req = new ProductionRequests.CreateMaterialIssueRequest(
            prId, List.of(new ProductionRequests.IssueItemRequest(101L, new BigDecimal("10.000"))), "Xuất đợt 1"
        );

        CurrentUser actor = new CurrentUser(50L, staffId, "staff01");
        ProductionViews.MaterialIssueRequestDetailView view = service.createMaterialIssueRequest(req, actor);

        assertNotNull(view);
        assertEquals(10L, view.id());
        assertEquals("PENDING", view.status());
        assertFalse(view.hasStockShortage());
    }

    @Test
    void mrp05b_createMaterialIssueRequestExceedingBomThrows409() {
        Long prId = 100L;
        Long staffId = 5L;
        ProductionRequest pr = createPr(prId, 10L, 1L, 10, 2L, staffId, ProductionRequestStatus.PENDING);
        when(prRepo.findById(prId)).thenReturn(Optional.of(pr));

        BomItem item = createBomItem(1001L, 1L, 101L, new BigDecimal("2.000")); // Quota: 20
        when(bomItemRepo.findByBomIdOrderByMaterialIdAsc(1L)).thenReturn(List.of(item));

        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(prId), eq(101L))).thenReturn(new BigDecimal("15.000"));

        // Yêu cầu thêm 10 -> tổng 25 > 20 -> 409
        ProductionRequests.CreateMaterialIssueRequest req = new ProductionRequests.CreateMaterialIssueRequest(
            prId, List.of(new ProductionRequests.IssueItemRequest(101L, new BigDecimal("10.000"))), "Vượt"
        );

        CurrentUser actor = new CurrentUser(50L, staffId, "staff01");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createMaterialIssueRequest(req, actor));
        assertTrue(ex.getMessage().contains("vượt định mức BOM"));
    }

    @Test
    void mrp06_approveMaterialIssueRequestSufficientStock() {
        Long mirId = 10L;
        MaterialIssueRequest mir = createMir(mirId, 100L, 5L, MaterialIssueRequestStatus.PENDING);
        when(mirRepo.findByIdForUpdate(mirId)).thenReturn(Optional.of(mir));

        MaterialIssueRequestItem item = new MaterialIssueRequestItem(mirId, 101L, new BigDecimal("10.000"));
        when(mirItemRepo.findByRequestId(mirId)).thenReturn(List.of(item));

        when(stockLedger.materialBalance(101L)).thenReturn(new BigDecimal("15.000"));
        when(mirRepo.findById(mirId)).thenReturn(Optional.of(mir));

        CurrentUser wmActor = new CurrentUser(1L, 2L, "wm01");
        ProductionViews.MaterialIssueRequestDetailView res = service.approveMaterialIssueRequest(mirId, wmActor);

        assertEquals("APPROVED", mir.getStatus().name());
        assertEquals(2L, mir.getReviewedBy());
        verify(stockLedger).post(eq(MovementType.OUT), eq(101L), any(), eq(new BigDecimal("10.000")), any(), eq(mirId), any(), any(), eq(1L), any());
    }

    @Test
    void mrp07_approveMaterialIssueRequestInsufficientStockThrows409() {
        Long mirId = 10L;
        MaterialIssueRequest mir = createMir(mirId, 100L, 5L, MaterialIssueRequestStatus.PENDING);
        when(mirRepo.findByIdForUpdate(mirId)).thenReturn(Optional.of(mir));

        MaterialIssueRequestItem item = new MaterialIssueRequestItem(mirId, 101L, new BigDecimal("10.000"));
        when(mirItemRepo.findByRequestId(mirId)).thenReturn(List.of(item));

        // Tồn chỉ còn 5 < 10
        when(stockLedger.materialBalance(101L)).thenReturn(new BigDecimal("5.000"));

        CurrentUser wmActor = new CurrentUser(1L, 2L, "wm01");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.approveMaterialIssueRequest(mirId, wmActor));
        assertTrue(ex.getMessage().contains("không đủ để xuất kho"));

        assertEquals(MaterialIssueRequestStatus.PENDING, mir.getStatus());
        verify(stockLedger, never()).post(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void mrp08_rejectMaterialIssueRequestSetsRejected() {
        Long mirId = 10L;
        MaterialIssueRequest mir = createMir(mirId, 100L, 5L, MaterialIssueRequestStatus.PENDING);
        when(mirRepo.findByIdForUpdate(mirId)).thenReturn(Optional.of(mir));
        when(mirRepo.findById(mirId)).thenReturn(Optional.of(mir));

        CurrentUser wmActor = new CurrentUser(1L, 2L, "wm01");
        service.rejectMaterialIssueRequest(mirId, new ProductionRequests.RejectRequest("Kho hết"), wmActor);

        assertEquals("REJECTED", mir.getStatus().name());
        assertTrue(mir.getNote().contains("[TỪ CHỐI] Kho hết"));
    }

    // =========================================================================
    // PRODUCTION EXECUTION TESTS (MRP-11, MRP-12)
    // =========================================================================

    @Test
    void mrp11_createProductionSucceedsAndSetsPrInProgress() {
        Long prId = 100L;
        Long staffId = 5L;
        ProductionRequest pr = createPr(prId, 10L, 1L, 10, 2L, staffId, ProductionRequestStatus.PENDING);
        when(prRepo.findById(prId)).thenReturn(Optional.of(pr));
        when(prodRepo.findByProductionRequestIdAndStatusNot(prId, ProductionStatus.CANCELLED)).thenReturn(List.of());

        Production savedProd = createProd(500L, prId, 4, staffId, ProductionStatus.IN_PROGRESS);
        when(prodRepo.saveAndFlush(any(Production.class))).thenReturn(savedProd);

        CurrentUser actor = new CurrentUser(50L, staffId, "staff01");
        ProductionRequests.CreateProductionExecutionRequest req = new ProductionRequests.CreateProductionExecutionRequest(prId, 4, "Đợt 1");

        ProductionViews.ProductionExecutionView view = service.createProduction(req, actor);

        assertNotNull(view);
        assertEquals(500L, view.id());
        assertEquals("IN_PROGRESS", view.status());
        assertEquals(4, view.quantity());
        assertEquals(ProductionRequestStatus.IN_PROGRESS, pr.getStatus());
    }

    @Test
    void mrp11b_createProductionExceedingQuantityThrows409() {
        Long prId = 100L;
        Long staffId = 5L;
        ProductionRequest pr = createPr(prId, 10L, 1L, 10, 2L, staffId, ProductionRequestStatus.IN_PROGRESS);
        when(prRepo.findById(prId)).thenReturn(Optional.of(pr));

        Production prodExisting = createProd(500L, prId, 8, staffId, ProductionStatus.IN_PROGRESS);
        when(prodRepo.findByProductionRequestIdAndStatusNot(prId, ProductionStatus.CANCELLED)).thenReturn(List.of(prodExisting));

        CurrentUser actor = new CurrentUser(50L, staffId, "staff01");
        // 8 + 4 = 12 > 10 -> 409
        ProductionRequests.CreateProductionExecutionRequest req = new ProductionRequests.CreateProductionExecutionRequest(prId, 4, "Vượt");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createProduction(req, actor));
        assertTrue(ex.getMessage().contains("vượt số lượng yêu cầu"));
    }

    @Test
    void mrp12_completeProductionFailsWhenMaterialsNotSufficientlyIssued() {
        Long prodId = 500L;
        Long prId = 100L;
        Long staffId = 5L;
        Production prod = createProd(prodId, prId, 4, staffId, ProductionStatus.IN_PROGRESS);
        when(prodRepo.findByIdForUpdate(prodId)).thenReturn(Optional.of(prod));

        ProductionRequest pr = createPr(prId, 10L, 1L, 10, 2L, staffId, ProductionRequestStatus.IN_PROGRESS);
        when(prRepo.findByIdForUpdate(prId)).thenReturn(Optional.of(pr));
        when(prodRepo.findByProductionRequestIdAndStatus(prId, ProductionStatus.COMPLETED)).thenReturn(List.of());

        // BOM cần 2 mặt bàn cho 1 sản phẩm -> 4 sp cần 8 mặt bàn
        BomItem bi = createBomItem(1001L, 1L, 101L, new BigDecimal("2.000"));
        when(bomItemRepo.findByBomIdOrderByMaterialIdAsc(1L)).thenReturn(List.of(bi));

        // Nhưng mới chỉ xuất approved 6 < 8
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(prId), eq(101L))).thenReturn(new BigDecimal("6.000"));

        CurrentUser actor = new CurrentUser(50L, staffId, "staff01");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.completeProduction(prodId, actor));
        assertTrue(ex.getMessage().contains("NVL đã xuất kho chưa đủ"));
    }

    @Test
    void mrp12b_completeProductionSucceedsAndSetsPrCompleted() {
        Long prodId = 501L;
        Long prId = 100L;
        Long staffId = 5L;
        // Đợt 2 làm nốt 6 cái (trước đó đã xong 4 cái)
        Production prod = createProd(prodId, prId, 6, staffId, ProductionStatus.IN_PROGRESS);
        when(prodRepo.findByIdForUpdate(prodId)).thenReturn(Optional.of(prod));

        ProductionRequest pr = createPr(prId, 10L, 1L, 10, 2L, staffId, ProductionRequestStatus.IN_PROGRESS);
        when(prRepo.findByIdForUpdate(prId)).thenReturn(Optional.of(pr));

        Production prod1Done = createProd(500L, prId, 4, staffId, ProductionStatus.COMPLETED);
        when(prodRepo.findByProductionRequestIdAndStatus(prId, ProductionStatus.COMPLETED)).thenReturn(List.of(prod1Done));

        // BOM cần 1 mặt bàn -> 10 cái cần 10 mặt bàn
        BomItem bi = createBomItem(1001L, 1L, 101L, new BigDecimal("1.000"));
        when(bomItemRepo.findByBomIdOrderByMaterialIdAsc(1L)).thenReturn(List.of(bi));

        // Đã xuất đủ 10
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(prId), eq(101L))).thenReturn(new BigDecimal("10.000"));

        CurrentUser actor = new CurrentUser(50L, staffId, "staff01");
        ProductionViews.ProductionExecutionView res = service.completeProduction(prodId, actor);

        assertEquals("COMPLETED", prod.getStatus().name());
        assertEquals(ProductionRequestStatus.COMPLETED, pr.getStatus()); // 4 + 6 = 10 == pr.quantity
        verify(stockLedger).post(eq(MovementType.IN), any(), eq(10L), eq(new BigDecimal("6")), any(), any(), eq(prodId), any(), eq(50L), any());
    }
}