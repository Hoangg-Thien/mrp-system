package mrp.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "category_id", nullable = false) private Long categoryId;
    @Column(nullable = false, length = 200) private String name;
    @Column(columnDefinition = "text") private String description;
    @Column(nullable = false, length = 50) private String unit;
    @Column(name = "selling_price", nullable = false, precision = 15, scale = 2) private BigDecimal sellingPrice;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    protected Product() { }
    @PrePersist void onCreate() { createdAt=LocalDateTime.now(); }
    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getUnit() { return unit; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setActive(boolean active) { this.active=active; }
    public void update(CatalogRequests.ProductRequest r) {
        categoryId=r.categoryId(); name=r.name().trim(); description=r.description(); unit=r.unit().trim(); sellingPrice=r.sellingPrice();
    }
}