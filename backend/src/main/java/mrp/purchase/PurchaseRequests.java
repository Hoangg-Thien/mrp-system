package mrp.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO yêu cầu cho phiếu nhập kho NVL (P4). Client chỉ gửi {@code supplierId} và {@code items};
 * server tính {@code amount} ({@code round(quantity × unit_price, 2)} [DB {@code ck_pri_amount}])
 * và {@code total_amount} ({@code Σ amount} [SVC, A6]); {@code status}, {@code receipt_date},
 * {@code warehouse_manager_id} do server / DB đặt.
 */
public final class PurchaseRequests {
    private PurchaseRequests() { }

    /** Một dòng phiếu nhập: {@code materialId} active và không trùng trong phiếu [DB PK kép], số lượng > 0, đơn giá ≥ 0. */
    public record PurchaseReceiptItemRequest(@NotNull @Positive Long materialId,
                                             @NotNull @Positive @Digits(integer = 12, fraction = 3) BigDecimal quantity,
                                             @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice) { }

    /** Body tạo / sửa phiếu nhập (sửa chỉ khi PENDING). {@code supplierId} phải tồn tại; {@code items} phải có ≥ 1 dòng. */
    public record PurchaseReceiptRequest(@NotNull @Positive Long supplierId,
                                         @NotEmpty List<@Valid PurchaseReceiptItemRequest> items) { }
}
