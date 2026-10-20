package mrp.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "materials")
public class Material {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, length = 50) private String unit;
    @Column(name = "standard_cost", nullable = false, precision = 15, scale = 2) private BigDecimal standardCost;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    protected Material() { }
    @PrePersist void onCreate() { createdAt=LocalDateTime.now(); }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public BigDecimal getStandardCost() { return standardCost; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setActive(boolean active) { this.active=active; }
    public void update(CatalogRequests.MaterialRequest r) {
        code=r.code().trim(); name=r.name().trim(); unit=r.unit().trim(); standardCost=r.standardCost();
    }
}