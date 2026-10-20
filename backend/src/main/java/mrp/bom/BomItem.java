package mrp.bom;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Thực thể {@code bom_items} — dòng chi tiết công thức sản phẩm (P5).
 * <ul>
 *   <li>{@code bom_id} trỏ {@code boms.id} (ON DELETE CASCADE).</li>
 *   <li>{@code material_id} trỏ {@code materials.id}.</li>
 *   <li>{@code quantity} số lượng NVL cần cho 1 đơn vị thành phẩm, scale 3, CHECK (quantity > 0).</li>
 *   <li>Trigger {@code trg_lock_bom_items} chặn sửa dòng khi BOM đã có production_request.</li>
 * </ul>
 */
@Entity
@Table(name = "bom_items")
public class BomItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bom_id", nullable = false)
    private Long bomId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    protected BomItem() { }

    public BomItem(Long bomId, Long materialId, BigDecimal quantity) {
        this.bomId = bomId;
        this.materialId = materialId;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getBomId() {
        return bomId;
    }

    public void setBomId(Long bomId) {
        this.bomId = bomId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
