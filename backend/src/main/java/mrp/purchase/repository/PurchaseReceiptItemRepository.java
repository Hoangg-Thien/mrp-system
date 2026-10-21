package mrp.purchase.repository;


import mrp.purchase.entity.PurchaseReceiptItem;
import mrp.purchase.entity.PurchaseReceiptItemId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repo {@code purchase_receipt_items}. Truy vấn dòng theo phiếu (sắp theo {@code materialId} để view ổn định),
 * đếm và xóa hàng loạt (cho PUT replace-items khi PENDING — trigger {@code trg_freeze_items} cho phép).
 * {@link #deleteByPurchaseReceiptId(Long)} là bulk DELETE ({@code @Modifying}) — thực thi ngay, trigger vẫn
 * fire từng dòng; đảm bảo DELETE chạy trước khi INSERT dòng mới (tránh xung đột PK kép).
 */
@Repository
public interface PurchaseReceiptItemRepository extends JpaRepository<PurchaseReceiptItem, PurchaseReceiptItemId> {
    List<PurchaseReceiptItem> findByPurchaseReceiptIdOrderByMaterialIdAsc(Long purchaseReceiptId);
    long countByPurchaseReceiptId(Long purchaseReceiptId);
    @Modifying
    @Query("delete from PurchaseReceiptItem i where i.purchaseReceiptId = :id")
    void deleteByPurchaseReceiptId(@Param("id") Long purchaseReceiptId);
}
