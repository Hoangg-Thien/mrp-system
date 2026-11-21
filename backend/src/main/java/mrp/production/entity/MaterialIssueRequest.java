package mrp.production.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "material_issue_requests")
public class MaterialIssueRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "production_request_id", nullable = false)
    private Long productionRequestId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MaterialIssueRequestStatus status = MaterialIssueRequestStatus.PENDING;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(columnDefinition = "text")
    private String note;

    protected MaterialIssueRequest() { }

    public MaterialIssueRequest(Long productionRequestId, Long requestedBy, String note) {
        this.productionRequestId = productionRequestId;
        this.requestedBy = requestedBy;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = MaterialIssueRequestStatus.PENDING;
        }
    }

    public Long getId() { return id; }
    public Long getProductionRequestId() { return productionRequestId; }
    public void setProductionRequestId(Long productionRequestId) { this.productionRequestId = productionRequestId; }
    public Long getRequestedBy() { return requestedBy; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public MaterialIssueRequestStatus getStatus() { return status; }
    public void setStatus(MaterialIssueRequestStatus status) { this.status = status; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}