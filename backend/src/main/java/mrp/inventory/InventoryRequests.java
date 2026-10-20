package mrp.inventory;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class InventoryRequests {
    private InventoryRequests() { }

    /** Hướng điều chỉnh: IN → ADJUST_IN, OUT → ADJUST_OUT. */
    public enum AdjustmentDirection { IN, OUT }

    /**
     * Điều chỉnh tồn (chỉ WM). Đúng một trong {@code materialId} / {@code productId}; số lượng > 0
     * (thành phẩm phải nguyên [DB]); {@code note} bắt buộc [DB ck_sm_adjust_note].
     */
    public record StockAdjustmentRequest(@Positive Long materialId, @Positive Long productId,
                                         @NotNull AdjustmentDirection direction,
                                         @NotNull @Positive @Digits(integer = 12, fraction = 3) BigDecimal quantity,
                                         @NotBlank @Size(max = 1000) String note) { }
}
