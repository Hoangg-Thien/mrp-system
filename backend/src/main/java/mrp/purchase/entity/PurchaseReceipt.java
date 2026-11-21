package mrp.purchase.entity;


import mrp.purchase.service.PurchaseService;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thực thể {@code purchase_receipts} — phiếu nhập kho nguyên vật liệu (P4).
 * <ul>
 *   <li>{@code receipt_date}, {@code status}, {@code total_amount} có DB DEFAULT; server đặt tường minh.</li>
 *   <li>{@code warehouse_manager_id} = profile id [DB FK → {@code warehouse_managers.id}] của WM tạo phiếu;
 *       movement {@code created_by} = {@code users.id} (khác — xem {@link PurchaseService#complete}).</li>
 *   <li>Trigger {@code trg_final_immutable} chặn UPDATE khi {@code status} đã final; service pre-check + DB backstop.</li>
 * </ul>
 * {@code ddl-auto: validate} — ánh xạ phải khớp schema.
 */
@Entity
@Table(name = "purchase_receipts")
public class PurchaseReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "supplier_id", nullable = false) private Long supplierId;
    @Column(name = "warehouse_manager_id", nullable = false, updatable = false) private Long warehouseManagerId;
    @Column(name = "receipt_date", nullable = false, updatable = false) private LocalDateTime receiptDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PurchaseReceiptStatus status;
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2) private BigDecimal totalAmount;

    protected PurchaseReceipt() { }
    public PurchaseReceipt(Long supplierId, Long warehouseManagerId) {
        this.supplierId = supplierId; this.warehouseManagerId = warehouseManagerId;
    }
    @PrePersist void onCreate() { if (receiptDate == null) receiptDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public Long getWarehouseManagerId() { return warehouseManagerId; }
    public LocalDateTime getReceiptDate() { return receiptDate; }
    public PurchaseReceiptStatus getStatus() { return status; }
    public void setStatus(PurchaseReceiptStatus status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}