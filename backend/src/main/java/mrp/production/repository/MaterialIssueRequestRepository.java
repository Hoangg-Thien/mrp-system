package mrp.production.repository;


import mrp.production.entity.MaterialIssueRequest;
import mrp.production.entity.MaterialIssueRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialIssueRequestRepository extends JpaRepository<MaterialIssueRequest, Long>, JpaSpecificationExecutor<MaterialIssueRequest> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT mir FROM MaterialIssueRequest mir WHERE mir.id = :id")
    Optional<MaterialIssueRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT mir.productionRequestId FROM MaterialIssueRequest mir WHERE mir.id = :id")
    Optional<Long> findProductionRequestIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT mir FROM MaterialIssueRequest mir WHERE mir.productionRequestId = :productionRequestId AND mir.status = :status ORDER BY mir.id")
    List<MaterialIssueRequest> findByProductionRequestIdAndStatusForUpdate(
            @Param("productionRequestId") Long productionRequestId,
            @Param("status") MaterialIssueRequestStatus status);

    List<MaterialIssueRequest> findByProductionRequestId(Long productionRequestId);

    List<MaterialIssueRequest> findByProductionRequestIdAndStatusIn(Long productionRequestId, Collection<MaterialIssueRequestStatus> statuses);

    boolean existsByProductionRequestIdAndStatus(Long productionRequestId, MaterialIssueRequestStatus status);
}
