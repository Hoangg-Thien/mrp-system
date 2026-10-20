package mrp.production;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionRequestRepository extends JpaRepository<ProductionRequest, Long>, JpaSpecificationExecutor<ProductionRequest> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pr FROM ProductionRequest pr WHERE pr.id = :id")
    Optional<ProductionRequest> findByIdForUpdate(@Param("id") Long id);

    List<ProductionRequest> findByProductIdAndStatusIn(Long productId, Collection<ProductionRequestStatus> statuses);

    List<ProductionRequest> findByStatusIn(Collection<ProductionRequestStatus> statuses);

    boolean existsByBomId(Long bomId);

    boolean existsByProductId(Long productId);

    boolean existsByAssignedTo(Long assignedTo);
}
