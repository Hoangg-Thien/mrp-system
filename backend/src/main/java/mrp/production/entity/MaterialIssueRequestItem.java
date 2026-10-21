package mrp.production.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "material_issue_request_items")
public class MaterialIssueRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    protected MaterialIssueRequestItem() { }

    public MaterialIssueRequestItem(Long requestId, Long materialId, BigDecimal quantity) {
        this.requestId = requestId;
        this.materialId = materialId;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
}