package mrp.fgissue.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "finished_goods_issue_request_items")
public class FinishedGoodsIssueRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    protected FinishedGoodsIssueRequestItem() { }

    public FinishedGoodsIssueRequestItem(Long requestId, Long productId, Integer quantity) {
        this.requestId = requestId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}