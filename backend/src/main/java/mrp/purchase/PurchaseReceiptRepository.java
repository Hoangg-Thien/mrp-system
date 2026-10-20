package mrp.purchase;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repo {@code purchase_receipts}. {@link #findByIdForUpdate(Long)} khóa bi quan [D12] để serialize
 * complete/cancel/replace-items đồng thời — match {@code findByIdForUpdate} của {@code InventoryService}.
 */
@Repository
public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceipt, Long>, JpaSpecificationExecutor<PurchaseReceipt> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PurchaseReceipt r where r.id = :id")
    Optional<PurchaseReceipt> findByIdForUpdate(@Param("id") Long id);
}
