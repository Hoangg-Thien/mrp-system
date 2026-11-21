package mrp.purchase;


import mrp.catalog.repository.MaterialRepository;
import mrp.catalog.repository.SupplierRepository;
import mrp.catalog.service.CatalogService;
import mrp.common.exception.BadRequestException;
import mrp.common.exception.BusinessException;
import mrp.common.exception.ResourceNotFoundException;
import mrp.inventory.entity.MovementType;
import mrp.inventory.service.StockLedger;
import mrp.purchase.dto.PurchaseRequests;
import mrp.purchase.entity.PurchaseReceipt;
import mrp.purchase.entity.PurchaseReceiptItem;
import mrp.purchase.entity.PurchaseReceiptStatus;
import mrp.purchase.repository.PurchaseReceiptItemRepository;
import mrp.purchase.repository.PurchaseReceiptRepository;
import mrp.purchase.service.PurchaseService;
import mrp.security.CurrentUser;
import mrp.user.repository.WarehouseManagerRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test {@link PurchaseService} (P4) theo pattern {@code AdminUserServiceTest}: mock toàn bộ
 * repo / dependency, kiểm tra logic nghiệp vụ (trạng thái, tính amount, post movement, freeze PENDING).
 * DB trigger / constraint không nằm trong phạm vi unit test.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {
    @Mock PurchaseReceiptRepository receipts;
    @Mock PurchaseReceiptItemRepository items;
    @Mock SupplierRepository suppliers;
    @Mock MaterialRepository materials;
    @Mock WarehouseManagerRepository managers;
    @Mock StockLedger ledger;
    @Mock CatalogService catalog;

    private PurchaseService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseService(receipts, items, suppliers, materials, managers, ledger, catalog);
    }

    @Test
    void pur01_createSetsPendingAndComputesTotal() {
        when(suppliers.existsById(2L)).thenReturn(true);
        when(receipts.saveAndFlush(any(PurchaseReceipt.class))).thenAnswer(inv -> {
            PurchaseReceipt r = inv.getArgument(0);
            setField(r, "id", 1L);
            return r;
        });

        service.create(new PurchaseRequests.PurchaseReceiptRequest(2L, List.of(
                new PurchaseRequests.PurchaseReceiptItemRequest(10L, new BigDecimal("5"), new BigDecimal("1000")),
                new PurchaseRequests.PurchaseReceiptItemRequest(11L, new BigDecimal("2"), new BigDecimal("500")))),
                new CurrentUser(7L, 5L, "wm01"));

        ArgumentCaptor<PurchaseReceipt> rc = ArgumentCaptor.forClass(PurchaseReceipt.class);
        verify(receipts).saveAndFlush(rc.capture());
        assertEquals(PurchaseReceiptStatus.PENDING, rc.getValue().getStatus());
        assertEquals(0, new BigDecimal("6000").compareTo(rc.getValue().getTotalAmount())); // 5*1000 + 2*500
        assertEquals(5L, rc.getValue().getWarehouseManagerId());
        verify(items, times(2)).save(any(PurchaseReceiptItem.class));
        verify(catalog).requireActiveMaterial(10L);
        verify(catalog).requireActiveMaterial(11L);
    }

    @Test
    void pur02_createRejectsUnknownSupplier() {
        when(suppliers.existsById(999L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.create(
                new PurchaseRequests.PurchaseReceiptRequest(999L, List.of(
                        new PurchaseRequests.PurchaseReceiptItemRequest(10L, new BigDecimal("5"), new BigDecimal("1000")))),
                new CurrentUser(7L, 5L, "wm01")));
    }

    @Test
    void pur03_createRejectsInactiveMaterial() {
        when(suppliers.existsById(2L)).thenReturn(true);
        when(catalog.requireActiveMaterial(10L)).thenThrow(new BusinessException("Nguyên vật liệu đã bị vô hiệu hóa"));
        assertThrows(BusinessException.class, () -> service.create(
                new PurchaseRequests.PurchaseReceiptRequest(2L, List.of(
                        new PurchaseRequests.PurchaseReceiptItemRequest(10L, new BigDecimal("5"), new BigDecimal("1000")))),
                new CurrentUser(7L, 5L, "wm01")));
    }

    @Test
    void pur04_createRejectsDuplicateMaterial() {
        when(suppliers.existsById(2L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.create(
                new PurchaseRequests.PurchaseReceiptRequest(2L, List.of(
                        new PurchaseRequests.PurchaseReceiptItemRequest(10L, new BigDecimal("5"), new BigDecimal("1000")),
                        new PurchaseRequests.PurchaseReceiptItemRequest(10L, new BigDecimal("2"), new BigDecimal("500")))),
                new CurrentUser(7L, 5L, "wm01")));
    }

    @Test
    void pur05_completePostsInboundMovements() {
        PurchaseReceipt receipt = receipt(1L, 2L, 5L, PurchaseReceiptStatus.PENDING);
        when(receipts.findByIdForUpdate(1L)).thenReturn(Optional.of(receipt));
        when(items.findByPurchaseReceiptIdOrderByMaterialIdAsc(1L)).thenReturn(List.of(
                new PurchaseReceiptItem(1L, 10L, new BigDecimal("5"), new BigDecimal("1000"), new BigDecimal("5000")),
                new PurchaseReceiptItem(1L, 11L, new BigDecimal("2"), new BigDecimal("500"), new BigDecimal("1000"))));

        service.complete(1L, new CurrentUser(7L, 5L, "wm01"));

        assertEquals(PurchaseReceiptStatus.COMPLETED, receipt.getStatus());
        verify(catalog).requireActiveMaterial(10L);
        verify(catalog).requireActiveMaterial(11L);
        // 2 dòng movement IN (theo từng dòng NVL), actor.userId() dùng cho created_by
        verify(ledger).post(eq(MovementType.IN), eq(10L), isNull(), eq(new BigDecimal("5")),
                eq(1L), isNull(), isNull(), isNull(), eq(7L), contains("phiếu nhập"));
        verify(ledger).post(eq(MovementType.IN), eq(11L), isNull(), eq(new BigDecimal("2")),
                eq(1L), isNull(), isNull(), isNull(), eq(7L), contains("phiếu nhập"));
    }

    @Test
    void pur06_completeRejectsNonPending() {
        when(receipts.findByIdForUpdate(1L)).thenReturn(
                Optional.of(receipt(1L, 2L, 5L, PurchaseReceiptStatus.COMPLETED)));
        assertThrows(BusinessException.class, () -> service.complete(1L, new CurrentUser(7L, 5L, "wm01")));
    }

    @Test
    void pur07_cancelSetsCancelled() {
        PurchaseReceipt receipt = receipt(1L, 2L, 5L, PurchaseReceiptStatus.PENDING);
        when(receipts.findByIdForUpdate(1L)).thenReturn(Optional.of(receipt));
        service.cancel(1L);
        assertEquals(PurchaseReceiptStatus.CANCELLED, receipt.getStatus());
        verify(receipts).saveAndFlush(receipt);
    }

    @Test
    void pur08_cancelRejectsNonPending() {
        when(receipts.findByIdForUpdate(1L)).thenReturn(
                Optional.of(receipt(1L, 2L, 5L, PurchaseReceiptStatus.COMPLETED)));
        assertThrows(BusinessException.class, () -> service.cancel(1L));
    }

    @Test
    void pur09_replaceItemsReplacesAndUpdatesTotal() {
        PurchaseReceipt receipt = receipt(1L, 2L, 5L, PurchaseReceiptStatus.PENDING);
        when(receipts.findByIdForUpdate(1L)).thenReturn(Optional.of(receipt));
        when(suppliers.existsById(3L)).thenReturn(true);

        service.replaceItems(1L, new PurchaseRequests.PurchaseReceiptRequest(3L, List.of(
                new PurchaseRequests.PurchaseReceiptItemRequest(10L, new BigDecimal("5"), new BigDecimal("1000")))));

        verify(items).deleteByPurchaseReceiptId(1L);
        verify(items).saveAll(anyList());
        ArgumentCaptor<PurchaseReceipt> rc = ArgumentCaptor.forClass(PurchaseReceipt.class);
        verify(receipts).saveAndFlush(rc.capture());
        assertEquals(3L, rc.getValue().getSupplierId());
        assertEquals(0, new BigDecimal("5000").compareTo(rc.getValue().getTotalAmount()));
    }

    @Test
    void pur10_replaceItemsRejectsCompleted() {
        when(receipts.findByIdForUpdate(1L)).thenReturn(
                Optional.of(receipt(1L, 2L, 5L, PurchaseReceiptStatus.COMPLETED)));
        assertThrows(BusinessException.class, () -> service.replaceItems(1L,
                new PurchaseRequests.PurchaseReceiptRequest(3L, List.of(
                        new PurchaseRequests.PurchaseReceiptItemRequest(10L, new BigDecimal("5"), new BigDecimal("1000"))))));
    }

    @Test
    void pur11_listRejectsInvalidPagination() {
        assertThrows(BadRequestException.class,
                () -> service.list(null, null, null, null, null, 0, 101, "id", "desc"));
    }

    // ---- helpers ----

    private static PurchaseReceipt receipt(Long id, Long supplierId, Long wmId, PurchaseReceiptStatus status) {
        PurchaseReceipt r = new PurchaseReceipt(supplierId, wmId);
        r.setStatus(status);
        r.setTotalAmount(BigDecimal.ZERO);
        setField(r, "id", id);
        return r;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}