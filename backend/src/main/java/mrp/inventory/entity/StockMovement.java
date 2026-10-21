package mrp.inventory.entity;


import mrp.inventory.service.StockLedger;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/**
 * Sổ kho append-only: {@code trg_sm_ro} chặn UPDATE / DELETE [DB]. Ghi mới qua {@link StockLedger};
 * entity này dùng cho đọc (lọc / phân trang) và để {@code ddl-auto: validate} kiểm tra mapping.
 * {@code created_by} trỏ {@code users.id} (không phải id bảng profile).
 */
@Entity
@Immutable
@Table(name = "stock_movements")
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "material_id") private Long materialId;
    @Column(name = "product_id") private Long productId;
    @Enumerated(EnumType.STRING) @Column(name = "movement_type", nullable = false, length = 20) private MovementType movementType;
    @Column(nullable = false, precision = 15, scale = 3) private BigDecimal quantity;
    @Column(name = "purchase_receipt_id") private Long purchaseReceiptId;
    @Column(name = "material_issue_request_id") private Long materialIssueRequestId;
    @Column(name = "production_id") private Long productionId;
    @Column(name = "fg_issue_request_id") private Long fgIssueRequestId;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private LocalDateTime createdAt;
    @Column(columnDefinition = "text") private String note;
    protected StockMovement() { }
    public Long getId() { return id; }
    public Long getMaterialId() { return materialId; }
    public Long getProductId() { return productId; }
    public MovementType getMovementType() { return movementType; }
    public BigDecimal getQuantity() { return quantity; }
    public Long getPurchaseReceiptId() { return purchaseReceiptId; }
    public Long getMaterialIssueRequestId() { return materialIssueRequestId; }
    public Long getProductionId() { return productionId; }
    public Long getFgIssueRequestId() { return fgIssueRequestId; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getNote() { return note; }
}