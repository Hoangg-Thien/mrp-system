package mrp.purchase;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read model của phiếu nhập kho NVL (P4). Trạng thái trả về dạng {@code String} (giá trị enum DB)
 * để client không phụ thuộc tên enum Java.
 */
public final class PurchaseViews {
    private PurchaseViews() { }

    /** Một dòng phiếu nhập kèm mã / tên NVL và đơn vị tính (lấy qua batch). */
    public record PurchaseReceiptItemView(Long materialId, String materialCode, String materialName, String unit,
                                          BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) { }

    /** Dòng danh sách phiếu nhập (không kèm items) kèm tên NCC và tên WM tạo phiếu. */
    public record PurchaseReceiptView(Long id, Long supplierId, String supplierName, Long warehouseManagerId,
                                      String warehouseManagerName, LocalDateTime receiptDate, String status,
                                      BigDecimal totalAmount) { }

    /** Chi tiết phiếu nhập: header + danh sách dòng (sắp xếp theo {@code materialId}). */
    public record PurchaseReceiptDetailView(Long id, Long supplierId, String supplierName, Long warehouseManagerId,
                                            String warehouseManagerName, LocalDateTime receiptDate, String status,
                                            BigDecimal totalAmount, List<PurchaseReceiptItemView> items) { }
}
