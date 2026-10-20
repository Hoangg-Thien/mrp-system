package mrp.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/**
 * Tồn kho chỉ đọc [DB]: dòng {@code inventories} do trigger {@code trg_sm_apply} tạo / cập nhật
 * khi INSERT {@code stock_movements}; {@code trg_inv_guard} chặn ghi trực tiếp. Entity immutable,
 * không có API nào sửa bảng này.
 */
@Entity
@Immutable
@Table(name = "inventories")
public class Inventory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "material_id") private Long materialId;
    @Column(name = "product_id") private Long productId;
    @Column(nullable = false, precision = 15, scale = 3) private BigDecimal quantity;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false) private LocalDateTime updatedAt;
    protected Inventory() { }
    public Long getId() { return id; }
    public Long getMaterialId() { return materialId; }
    public Long getProductId() { return productId; }
    public BigDecimal getQuantity() { return quantity; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
