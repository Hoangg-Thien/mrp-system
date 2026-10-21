package mrp.inventory.dto;


import mrp.inventory.entity.MovementType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class InventoryViews {
    private InventoryViews() { }

    /** Tồn NVL; {@code quantity} = 0 khi chưa có dòng inventories, {@code inventoryId} null khi chưa phát sinh. */
    public record MaterialStockView(Long materialId, String code, String name, String unit, boolean active,
                                    BigDecimal quantity, Long inventoryId, LocalDateTime updatedAt) { }

    /** Tồn thành phẩm; {@code quantity} luôn nguyên theo CHECK của schema. */
    public record ProductStockView(Long productId, String name, String unit, boolean active, Long categoryId,
                                   String categoryName, BigDecimal quantity, Long inventoryId,
                                   LocalDateTime updatedAt) { }

    /** Một dòng sổ kho, kèm tên vật tư / sản phẩm và người ghi (created_by = users.id). */
    public record StockMovementView(Long id, MovementType movementType, BigDecimal quantity,
                                    Long materialId, String materialCode, String materialName,
                                    Long productId, String productName, String unit,
                                    Long purchaseReceiptId, Long materialIssueRequestId, Long productionId,
                                    Long fgIssueRequestId, String sourceType, Long sourceId,
                                    Long createdBy, String createdByUsername, LocalDateTime createdAt, String note) { }

    /** Kết quả điều chỉnh tồn: movement vừa ghi và tồn trước / sau (đọc lại bằng query mới). */
    public record StockAdjustmentView(Long movementId, MovementType movementType, Long materialId, String materialCode,
                                      String materialName, Long productId, String productName, String unit,
                                      BigDecimal quantity, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                      String note, Long createdBy, String createdByUsername, LocalDateTime createdAt) { }
}