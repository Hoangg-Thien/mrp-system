package mrp.fgissue;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "finished_goods_issue_requests")
public class FinishedGoodsIssueRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FgIssueRequestStatus status = FgIssueRequestStatus.PENDING;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(columnDefinition = "text")
    private String note;

    protected FinishedGoodsIssueRequest() { }

    public FinishedGoodsIssueRequest(Long requestedBy, String note) {
        this.requestedBy = requestedBy;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = FgIssueRequestStatus.PENDING;
        }
    }

    public Long getId() { return id; }
    public Long getRequestedBy() { return requestedBy; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public FgIssueRequestStatus getStatus() { return status; }
    public void setStatus(FgIssueRequestStatus status) { this.status = status; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
