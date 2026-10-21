package mrp.production.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "production_requests")
public class ProductionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "bom_id", nullable = false)
    private Long bomId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "warehouse_manager_id", nullable = false)
    private Long warehouseManagerId;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductionRequestStatus status = ProductionRequestStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(columnDefinition = "text")
    private String note;

    protected ProductionRequest() { }

    public ProductionRequest(Long productId, Long bomId, Integer quantity, Long warehouseManagerId, Long assignedTo, String note) {
        this.productId = productId;
        this.bomId = bomId;
        this.quantity = quantity;
        this.warehouseManagerId = warehouseManagerId;
        this.assignedTo = assignedTo;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ProductionRequestStatus.PENDING;
        }
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getBomId() { return bomId; }
    public void setBomId(Long bomId) { this.bomId = bomId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getWarehouseManagerId() { return warehouseManagerId; }
    public void setWarehouseManagerId(Long warehouseManagerId) { this.warehouseManagerId = warehouseManagerId; }
    public Long getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Long assignedTo) { this.assignedTo = assignedTo; }
    public ProductionRequestStatus getStatus() { return status; }
    public void setStatus(ProductionRequestStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}