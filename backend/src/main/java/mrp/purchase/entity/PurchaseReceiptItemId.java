package mrp.purchase.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Khóa chính kép của {@code purchase_receipt_items} [DB PK {@code (purchase_receipt_id, material_id)}].
 * Dùng với {@code @IdClass} trên {@link PurchaseReceiptItem} để Hibernate quản lý thực thể có khóa kép.
 */
public class PurchaseReceiptItemId implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long purchaseReceiptId;
    private Long materialId;

    public PurchaseReceiptItemId() { }
    public PurchaseReceiptItemId(Long purchaseReceiptId, Long materialId) {
        this.purchaseReceiptId = purchaseReceiptId;
        this.materialId = materialId;
    }
    public Long getPurchaseReceiptId() { return purchaseReceiptId; }
    public Long getMaterialId() { return materialId; }
    public void setPurchaseReceiptId(Long purchaseReceiptId) { this.purchaseReceiptId = purchaseReceiptId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PurchaseReceiptItemId other)) return false;
        return Objects.equals(purchaseReceiptId, other.purchaseReceiptId)
                && Objects.equals(materialId, other.materialId);
    }
    @Override
    public int hashCode() { return Objects.hash(purchaseReceiptId, materialId); }
}