package mrp.fgissue;


import mrp.catalog.dto.CatalogRequests;
import mrp.catalog.entity.Product;
import mrp.catalog.repository.ProductRepository;
import mrp.catalog.service.CatalogService;
import mrp.common.exception.BadRequestException;
import mrp.common.exception.BusinessException;
import mrp.common.exception.ResourceNotFoundException;
import mrp.fgissue.dto.FgIssueRequests;
import mrp.fgissue.dto.FgIssueViews;
import mrp.fgissue.entity.FgIssueRequestStatus;
import mrp.fgissue.entity.FinishedGoodsIssueRequest;
import mrp.fgissue.entity.FinishedGoodsIssueRequestItem;
import mrp.fgissue.repository.FinishedGoodsIssueRequestItemRepository;
import mrp.fgissue.repository.FinishedGoodsIssueRequestRepository;
import mrp.fgissue.service.FgIssueService;
import mrp.inventory.entity.MovementType;
import mrp.inventory.service.StockLedger;
import mrp.security.CurrentUser;
import mrp.user.entity.Staff;
import mrp.user.entity.User;
import mrp.user.entity.UserRole;
import mrp.user.entity.WarehouseManager;
import mrp.user.repository.StaffRepository;
import mrp.user.repository.UserRepository;
import mrp.user.repository.WarehouseManagerRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class FgIssueServiceTest {

    @Mock private FinishedGoodsIssueRequestRepository requestRepo;
    @Mock private FinishedGoodsIssueRequestItemRepository itemRepo;
    @Mock private ProductRepository productRepo;
    @Mock private StaffRepository staffRepo;
    @Mock private WarehouseManagerRepository wmRepo;
    @Mock private UserRepository userRepo;
    @Mock private CatalogService catalogService;
    @Mock private StockLedger stockLedger;
    @Mock private JdbcTemplate jdbc;

    private FgIssueService service;

    @BeforeEach
    void setUp() {
        service = new FgIssueService(
            requestRepo, itemRepo, productRepo, staffRepo,
            wmRepo, userRepo, catalogService, stockLedger, jdbc
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

    private FinishedGoodsIssueRequest createFgir(Long id, Long requestedBy, FgIssueRequestStatus status, String note) {
        FinishedGoodsIssueRequest req = new FinishedGoodsIssueRequest(requestedBy, note);
        req.setStatus(status);
        try {
            Field fId = FinishedGoodsIssueRequest.class.getDeclaredField("id");
            fId.setAccessible(true);
            fId.set(req, id);
            Field fAt = FinishedGoodsIssueRequest.class.getDeclaredField("requestedAt");
            fAt.setAccessible(true);
            fAt.set(req, LocalDateTime.now());
        } catch (Exception ignored) { }
        return req;
    }

    private FinishedGoodsIssueRequestItem createItem(Long id, Long requestId, Long productId, int quantity) {
        FinishedGoodsIssueRequestItem item = new FinishedGoodsIssueRequestItem(requestId, productId, quantity);
        try {
            Field fId = FinishedGoodsIssueRequestItem.class.getDeclaredField("id");
            fId.setAccessible(true);
            fId.set(item, id);
        } catch (Exception ignored) { }
        return item;
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

    private WarehouseManager createWm(Long id, Long userId, String name) {
        WarehouseManager wm = new WarehouseManager(userId, name, "wm@test.com", "0987654321", "HCM");
        try {
            Field f = WarehouseManager.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(wm, id);
        } catch (Exception ignored) { }
        return wm;
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
    // FG-01: TẠO PHIẾU HỢP LỆ (PENDING, KHÔNG MOVEMENT)
    // =========================================================================

    @Test
    void fg01_createValidRequest_success() {
        CurrentUser actor = new CurrentUser(10L, 100L, "staff1");
        Product p = createProduct(1L, "Bàn trà", "cái", true);

        when(stockLedger.productBalance(1L)).thenReturn(new BigDecimal("10.000"));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(1L))).thenReturn(new BigDecimal("2.000"));

        doAnswer(inv -> {
            FinishedGoodsIssueRequest req = inv.getArgument(0);
            Field f = FinishedGoodsIssueRequest.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(req, 1L);
            Field fAt = FinishedGoodsIssueRequest.class.getDeclaredField("requestedAt");
            fAt.setAccessible(true);
            fAt.set(req, LocalDateTime.now());
            return req;
        }).when(requestRepo).saveAndFlush(any());

        when(requestRepo.findById(1L)).thenAnswer(inv -> {
            FinishedGoodsIssueRequest r = createFgir(1L, 100L, FgIssueRequestStatus.PENDING, "Xuất bán KH A");
            return Optional.of(r);
        });

        Staff staff = createStaff(100L, 10L, "Nguyen Van A");
        when(staffRepo.findById(100L)).thenReturn(Optional.of(staff));

        FinishedGoodsIssueRequestItem savedItem = createItem(50L, 1L, 1L, 5);
        when(itemRepo.findByRequestId(1L)).thenReturn(List.of(savedItem));
        when(productRepo.findAllById(Set.of(1L))).thenReturn(List.of(p));

        FgIssueRequests.CreateFgIssueRequest req = new FgIssueRequests.CreateFgIssueRequest(
            List.of(new FgIssueRequests.FgIssueItemRequest(1L, 5)),
            "Xuất bán KH A"
        );

        FgIssueViews.FgIssueRequestDetailView detail = service.create(req, actor);

        assertNotNull(detail);
        assertEquals(1L, detail.id());
        assertEquals("PENDING", detail.status());
        assertEquals("Nguyen Van A", detail.requestedByName());
        assertEquals(1, detail.items().size());
        assertEquals(5, detail.items().get(0).quantity());
        assertFalse(detail.hasStockShortage());
        assertNull(detail.warning());

        // Kiểm tra không có stock_movement nào được ghi
        verify(stockLedger, never()).post(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // =========================================================================
    // FG-02: PHIẾU CÓ SẢN PHẨM TRÙNG HOẶC INACTIVE -> LỖI
    // =========================================================================

    @Test
    void fg02_createRequest_duplicateProductInRequest_throwsBusinessException() {
        CurrentUser actor = new CurrentUser(10L, 100L, "staff1");
        FgIssueRequests.CreateFgIssueRequest req = new FgIssueRequests.CreateFgIssueRequest(
            List.of(
                new FgIssueRequests.FgIssueItemRequest(1L, 5),
                new FgIssueRequests.FgIssueItemRequest(1L, 3)
            ),
            "Xuất bán"
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req, actor));
        assertTrue(ex.getMessage().contains("trùng lặp"));
        verify(requestRepo, never()).saveAndFlush(any());
    }

    @Test
    void fg02b_createRequest_inactiveProduct_throwsBusinessException() {
        CurrentUser actor = new CurrentUser(10L, 100L, "staff1");
        doThrow(new BusinessException("Sản phẩm không hoạt động"))
            .when(catalogService).requireActiveProduct(2L);

        FgIssueRequests.CreateFgIssueRequest req = new FgIssueRequests.CreateFgIssueRequest(
            List.of(new FgIssueRequests.FgIssueItemRequest(2L, 5)),
            "Xuất bán"
        );

        assertThrows(BusinessException.class, () -> service.create(req, actor));
        verify(requestRepo, never()).saveAndFlush(any());
    }

    // =========================================================================
    // FG-03: XIN VƯỢT TỒN -> TẠO ĐƯỢC, RESPONSE CÓ CẢNH BÁO
    // =========================================================================

    @Test
    void fg03_createRequest_stockShortage_warningReturned() {
        CurrentUser actor = new CurrentUser(10L, 100L, "staff1");
        Product p = createProduct(1L, "Bàn trà", "cái", true);

        // Tồn hiện tại 10, các phiếu PENDING khác đã xin 8, phiếu này xin thêm 5 -> tổng 13 > 10
        when(stockLedger.productBalance(1L)).thenReturn(new BigDecimal("10.000"));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(1L))).thenReturn(new BigDecimal("8.000"));

        doAnswer(inv -> {
            FinishedGoodsIssueRequest req = inv.getArgument(0);
            Field f = FinishedGoodsIssueRequest.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(req, 2L);
            Field fAt = FinishedGoodsIssueRequest.class.getDeclaredField("requestedAt");
            fAt.setAccessible(true);
            fAt.set(req, LocalDateTime.now());
            return req;
        }).when(requestRepo).saveAndFlush(any());

        when(requestRepo.findById(2L)).thenAnswer(inv -> {
            FinishedGoodsIssueRequest r = createFgir(2L, 100L, FgIssueRequestStatus.PENDING, "Xuất vượt tồn");
            return Optional.of(r);
        });

        Staff staff = createStaff(100L, 10L, "Nguyen Van A");
        when(staffRepo.findById(100L)).thenReturn(Optional.of(staff));

        FinishedGoodsIssueRequestItem savedItem = createItem(51L, 2L, 1L, 5);
        when(itemRepo.findByRequestId(2L)).thenReturn(List.of(savedItem));
        when(productRepo.findAllById(Set.of(1L))).thenReturn(List.of(p));

        FgIssueRequests.CreateFgIssueRequest req = new FgIssueRequests.CreateFgIssueRequest(
            List.of(new FgIssueRequests.FgIssueItemRequest(1L, 5)),
            "Xuất vượt tồn"
        );

        FgIssueViews.FgIssueRequestDetailView detail = service.create(req, actor);

        assertNotNull(detail);
        assertEquals("PENDING", detail.status());
        assertTrue(detail.hasStockShortage());
        assertNotNull(detail.warning());
        assertTrue(detail.warning().contains("vượt quá tồn kho"));
    }

    // =========================================================================
    // FG-04: APPROVE ĐỦ TỒN -> APPROVED, STOCK MOVEMENT OUT TỪNG DÒNG
    // =========================================================================

    @Test
    void fg04_approve_sufficientStock_statusApprovedAndPostsStockOut() {
        CurrentUser wmActor = new CurrentUser(20L, 200L, "wm1");
        FinishedGoodsIssueRequest fgir = createFgir(10L, 100L, FgIssueRequestStatus.PENDING, "Xuất bán 2 SP");
        when(requestRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(fgir));

        FinishedGoodsIssueRequestItem item1 = createItem(101L, 10L, 1L, 5);
        FinishedGoodsIssueRequestItem item2 = createItem(102L, 10L, 2L, 3);
        when(itemRepo.findByRequestId(10L)).thenReturn(List.of(item1, item2));

        when(stockLedger.productBalance(1L)).thenReturn(new BigDecimal("10.000"));
        when(stockLedger.productBalance(2L)).thenReturn(new BigDecimal("5.000"));

        Product p1 = createProduct(1L, "SP 1", "cái", true);
        Product p2 = createProduct(2L, "SP 2", "cái", true);
        when(productRepo.findAllById(Set.of(1L, 2L))).thenReturn(List.of(p1, p2));

        when(requestRepo.findById(10L)).thenReturn(Optional.of(fgir));
        Staff staff = createStaff(100L, 10L, "Staff A");
        when(staffRepo.findById(100L)).thenReturn(Optional.of(staff));
        WarehouseManager wm = createWm(200L, 20L, "Warehouse Mgr");
        when(wmRepo.findById(200L)).thenReturn(Optional.of(wm));

        FgIssueViews.FgIssueRequestDetailView detail = service.approve(10L, wmActor);

        assertEquals("APPROVED", detail.status());
        assertEquals("Warehouse Mgr", detail.reviewedByName());
        assertNotNull(detail.reviewedAt());

        // Kiểm tra đúng 2 lần gọi stockLedger.post với MovementType.OUT
        verify(stockLedger).post(
            eq(MovementType.OUT),
            eq(null),
            eq(1L),
            eq(new BigDecimal("5")),
            eq(null),
            eq(null),
            eq(null),
            eq(10L),
            eq(20L),
            anyString()
        );
        verify(stockLedger).post(
            eq(MovementType.OUT),
            eq(null),
            eq(2L),
            eq(new BigDecimal("3")),
            eq(null),
            eq(null),
            eq(null),
            eq(10L),
            eq(20L),
            anyString()
        );
    }

    // =========================================================================
    // FG-04b: APPROVE THIẾU TỒN MỘT DÒNG -> 409, PHIẾU PENDING, KHÔNG MOVEMENT NÀO
    // =========================================================================

    @Test
    void fg04b_approve_insufficientStock_throwsBusinessException_noStockPost() {
        CurrentUser wmActor = new CurrentUser(20L, 200L, "wm1");
        FinishedGoodsIssueRequest fgir = createFgir(11L, 100L, FgIssueRequestStatus.PENDING, "Xuất bán thiếu tồn");
        when(requestRepo.findByIdForUpdate(11L)).thenReturn(Optional.of(fgir));

        FinishedGoodsIssueRequestItem item1 = createItem(101L, 11L, 1L, 5);
        FinishedGoodsIssueRequestItem item2 = createItem(102L, 11L, 2L, 10); // yêu cầu 10 nhưng tồn chỉ có 4
        when(itemRepo.findByRequestId(11L)).thenReturn(List.of(item1, item2));

        when(stockLedger.productBalance(1L)).thenReturn(new BigDecimal("10.000"));
        when(stockLedger.productBalance(2L)).thenReturn(new BigDecimal("4.000"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(11L, wmActor));
        assertTrue(ex.getMessage().contains("không đủ để xuất kho"));

        // Phiếu vẫn PENDING, không gọi stockLedger.post lần nào
        assertEquals(FgIssueRequestStatus.PENDING, fgir.getStatus());
        verify(stockLedger, never()).post(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // =========================================================================
    // FG-05: APPROVE / REJECT / CANCEL LẦN HAI -> 409
    // =========================================================================

    @Test
    void fg05_approve_nonPendingRequest_throwsBusinessException() {
        CurrentUser wmActor = new CurrentUser(20L, 200L, "wm1");
        FinishedGoodsIssueRequest fgir = createFgir(12L, 100L, FgIssueRequestStatus.APPROVED, "Đã duyệt");
        when(requestRepo.findByIdForUpdate(12L)).thenReturn(Optional.of(fgir));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(12L, wmActor));
        assertTrue(ex.getMessage().contains("Chỉ được duyệt phiếu ở trạng thái PENDING"));
        verify(stockLedger, never()).post(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void fg05b_reject_nonPendingRequest_throwsBusinessException() {
        CurrentUser wmActor = new CurrentUser(20L, 200L, "wm1");
        FinishedGoodsIssueRequest fgir = createFgir(13L, 100L, FgIssueRequestStatus.CANCELLED, "Đã hủy");
        when(requestRepo.findByIdForUpdate(13L)).thenReturn(Optional.of(fgir));

        BusinessException ex = assertThrows(BusinessException.class, () ->
            service.reject(13L, new FgIssueRequests.RejectRequest("Lý do từ chối"), wmActor)
        );
        assertTrue(ex.getMessage().contains("Chỉ được từ chối phiếu ở trạng thái PENDING"));
    }

    // =========================================================================
    // FG-06: REJECT PHIẾU PENDING -> REJECTED, NỐI [TỪ CHỐI] <LÝ DO>
    // =========================================================================

    @Test
    void fg06_reject_pendingRequest_statusRejectedAndAppendsReason() {
        CurrentUser wmActor = new CurrentUser(20L, 200L, "wm1");
        FinishedGoodsIssueRequest fgir = createFgir(14L, 100L, FgIssueRequestStatus.PENDING, "Ghi chú ban đầu");
        when(requestRepo.findByIdForUpdate(14L)).thenReturn(Optional.of(fgir));
        when(requestRepo.findById(14L)).thenReturn(Optional.of(fgir));

        Staff staff = createStaff(100L, 10L, "Staff A");
        when(staffRepo.findById(100L)).thenReturn(Optional.of(staff));
        WarehouseManager wm = createWm(200L, 20L, "Warehouse Mgr");
        when(wmRepo.findById(200L)).thenReturn(Optional.of(wm));

        when(itemRepo.findByRequestId(14L)).thenReturn(List.of());
        when(productRepo.findAllById(any())).thenReturn(List.of());

        FgIssueViews.FgIssueRequestDetailView detail = service.reject(
            14L, new FgIssueRequests.RejectRequest("Khách hàng đổi ý hủy đơn"), wmActor
        );

        assertEquals("REJECTED", detail.status());
        assertEquals("Warehouse Mgr", detail.reviewedByName());
        assertTrue(detail.note().contains("[TỪ CHỐI] Khách hàng đổi ý hủy đơn"));
        verify(stockLedger, never()).post(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // =========================================================================
    // FG-07: CANCEL PHIẾU BỞI NGƯỜI TẠO (PENDING -> CANCELLED); NGƯỜI KHÁC -> 404
    // =========================================================================

    @Test
    void fg07_cancel_byRequester_statusCancelled() {
        CurrentUser staffActor = new CurrentUser(10L, 100L, "staff1");
        FinishedGoodsIssueRequest fgir = createFgir(15L, 100L, FgIssueRequestStatus.PENDING, "Ghi chú");
        when(requestRepo.findByIdForUpdate(15L)).thenReturn(Optional.of(fgir));
        when(requestRepo.findById(15L)).thenReturn(Optional.of(fgir));

        Staff staff = createStaff(100L, 10L, "Staff A");
        when(staffRepo.findById(100L)).thenReturn(Optional.of(staff));
        when(itemRepo.findByRequestId(15L)).thenReturn(List.of());
        when(productRepo.findAllById(any())).thenReturn(List.of());

        FgIssueViews.FgIssueRequestDetailView detail = service.cancel(15L, staffActor);

        assertEquals("CANCELLED", detail.status());
        verify(stockLedger, never()).post(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void fg07b_cancel_byOtherStaff_throwsResourceNotFound() {
        CurrentUser otherStaffActor = new CurrentUser(99L, 999L, "staff2");
        FinishedGoodsIssueRequest fgir = createFgir(15L, 100L, FgIssueRequestStatus.PENDING, "Ghi chú");
        when(requestRepo.findByIdForUpdate(15L)).thenReturn(Optional.of(fgir));

        assertThrows(ResourceNotFoundException.class, () -> service.cancel(15L, otherStaffActor));
        assertEquals(FgIssueRequestStatus.PENDING, fgir.getStatus());
    }

    @Test
    void fg07c_cancel_nonPendingRequest_throwsBusinessException() {
        CurrentUser staffActor = new CurrentUser(10L, 100L, "staff1");
        FinishedGoodsIssueRequest fgir = createFgir(16L, 100L, FgIssueRequestStatus.APPROVED, "Đã duyệt");
        when(requestRepo.findByIdForUpdate(16L)).thenReturn(Optional.of(fgir));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(16L, staffActor));
        assertTrue(ex.getMessage().contains("Chỉ được hủy phiếu ở trạng thái PENDING"));
    }

    // =========================================================================
    // FG-08: STAFF XEM PHIẾU CỦA NGƯỜI KHÁC -> 404 (D11); WM XEM THÌ ĐƯỢC
    // =========================================================================

    @Test
    void fg08_detail_staffViewsOtherStaffSlip_throwsResourceNotFound() {
        CurrentUser staffB = new CurrentUser(99L, 999L, "staffB");
        FinishedGoodsIssueRequest fgir = createFgir(20L, 100L, FgIssueRequestStatus.PENDING, "Phiếu của Staff A");
        when(requestRepo.findById(20L)).thenReturn(Optional.of(fgir));

        User userB = createUser(99L, "staffB", UserRole.STAFF, true);
        when(userRepo.findById(99L)).thenReturn(Optional.of(userB));

        assertThrows(ResourceNotFoundException.class, () -> service.detail(20L, staffB));
    }

    @Test
    void fg08b_detail_wmViewsStaffSlip_succeeds() {
        CurrentUser wmActor = new CurrentUser(20L, 200L, "wm1");
        FinishedGoodsIssueRequest fgir = createFgir(20L, 100L, FgIssueRequestStatus.PENDING, "Phiếu của Staff A");
        when(requestRepo.findById(20L)).thenReturn(Optional.of(fgir));

        User userWm = createUser(20L, "wm1", UserRole.WAREHOUSE_MANAGER, true);
        when(userRepo.findById(20L)).thenReturn(Optional.of(userWm));

        Staff staffA = createStaff(100L, 10L, "Staff A");
        when(staffRepo.findById(100L)).thenReturn(Optional.of(staffA));
        when(itemRepo.findByRequestId(20L)).thenReturn(List.of());
        when(productRepo.findAllById(any())).thenReturn(List.of());

        FgIssueViews.FgIssueRequestDetailView detail = service.detail(20L, wmActor);
        assertNotNull(detail);
        assertEquals(20L, detail.id());
    }

    // =========================================================================
    // FG-09: LIST PHIẾU XUẤT THÀNH PHẨM (STAFF CHỈ XEM ĐƯỢC CỦA MÌNH)
    // =========================================================================

    @Test
    void fg09_list_staffForcesRequestedByFilter() {
        CurrentUser staffActor = new CurrentUser(10L, 100L, "staff1");
        FinishedGoodsIssueRequest fgir = createFgir(30L, 100L, FgIssueRequestStatus.PENDING, "Note");

        when(requestRepo.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(fgir)));

        Staff staff = createStaff(100L, 10L, "Staff 1");
        when(staffRepo.findAllById(Set.of(100L))).thenReturn(List.of(staff));
        when(wmRepo.findAllById(Set.of())).thenReturn(List.of());

        FinishedGoodsIssueRequestItem item = createItem(301L, 30L, 1L, 10);
        when(itemRepo.findByRequestIdIn(List.of(30L))).thenReturn(List.of(item));

        Page<FgIssueViews.FgIssueRequestView> page = service.list(
            null, null, null, 0, 20, "id", "desc", staffActor, true
        );

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
        assertEquals(10, page.getContent().get(0).totalQuantity());
        assertEquals(1, page.getContent().get(0).itemCount());
    }

    // =========================================================================
    // FG-10: SẮP XẾP / PHÂN TRANG HỢP LỆ & BẤT HỢP LỆ
    // =========================================================================

    @Test
    void fg10_query_invalidSortField_throwsBadRequestException() {
        CurrentUser actor = new CurrentUser(10L, 100L, "staff1");
        assertThrows(BadRequestException.class, () ->
            service.list(null, null, null, 0, 20, "invalidField", "asc", actor, false)
        );
    }

    @Test
    void fg10b_query_invalidDirection_throwsBadRequestException() {
        CurrentUser actor = new CurrentUser(10L, 100L, "staff1");
        assertThrows(BadRequestException.class, () ->
            service.list(null, null, null, 0, 20, "id", "random", actor, false)
        );
    }
}