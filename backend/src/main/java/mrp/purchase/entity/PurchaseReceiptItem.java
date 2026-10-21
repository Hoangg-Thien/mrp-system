package mrp.purchase.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Thực thể {@code purchase_receipt_items} — dòng phiếu nhập (P4).
 * <ul>
 *   <li>Khóa kép [DB PK {@code (purchase_receipt_id, material_id)}] → {@code @IdClass(PurchaseReceiptItemId.class)}.</li>
 *   <li>{@code amount = round(quantity * unit_price, 2)} [DB CHECK {@code ck_pri_amount}] — server tính bằng
 *       {@code quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP)} để khớp PostgreSQL {@code round(numeric,int)}.</li>
 *   <li>Trigger {@code trg_freeze_items} chặn INSERT/UPDATE/DELETE khi phiếu cha đã final (COMPLETED/CANCELLED).</li>
 * </ul>
 */
@Entity
@Table(name = "purchase_receipt_items")
@IdClass(PurchaseReceiptItemId.class)
public class PurchaseReceiptItem {
    @Id @Column(name = "purchase_receipt_id", updatable = false) private Long purchaseReceiptId;
    @Id @Column(name = "material_id", updatable = false) private Long materialId;
    @Column(nullable = false, precision = 15, scale = 3) private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;

    protected PurchaseReceiptItem() { }
    public PurchaseReceiptItem(Long purchaseReceiptId, Long materialId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
        this.purchaseReceiptId = purchaseReceiptId; this.materialId = materialId;
        this.quantity = quantity; this.unitPrice = unitPrice; this.amount = amount;
    }

    public Long getPurchaseReceiptId() { return purchaseReceiptId; }
    public void setPurchaseReceiptId(Long purchaseReceiptId) { this.purchaseReceiptId = purchaseReceiptId; }
    public Long getMaterialId() { return materialId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}