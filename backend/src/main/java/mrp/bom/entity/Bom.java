package mrp.bom.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Thực thể {@code boms} — công thức sản phẩm (P5).
 * <ul>
 *   <li>{@code product_id} trỏ {@code products.id}.</li>
 *   <li>{@code version} tự tăng theo từng sản phẩm (1, 2, 3...).</li>
 *   <li>{@code is_active}: chỉ tối đa 1 BOM active cho mỗi sản phẩm (partial unique index uq_boms_one_active).</li>
 * </ul>
 */
@Entity
@Table(name = "boms")
public class Bom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Bom() { }

    public Bom(Long productId, Integer version, boolean active) {
        this.productId = productId;
        this.version = version;
        this.active = active;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}