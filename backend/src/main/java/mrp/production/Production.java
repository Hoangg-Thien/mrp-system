package mrp.production;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "productions")
public class Production {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "production_request_id", nullable = false)
    private Long productionRequestId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "produced_by", nullable = false, updatable = false)
    private Long producedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductionStatus status = ProductionStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(columnDefinition = "text")
    private String note;

    protected Production() { }

    public Production(Long productionRequestId, Integer quantity, Long producedBy, String note) {
        this.productionRequestId = productionRequestId;
        this.quantity = quantity;
        this.producedBy = producedBy;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ProductionStatus.IN_PROGRESS;
        }
    }

    public Long getId() { return id; }
    public Long getProductionRequestId() { return productionRequestId; }
    public void setProductionRequestId(Long productionRequestId) { this.productionRequestId = productionRequestId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getProducedBy() { return producedBy; }
    public ProductionStatus getStatus() { return status; }
    public void setStatus(ProductionStatus status) { this.status = status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
