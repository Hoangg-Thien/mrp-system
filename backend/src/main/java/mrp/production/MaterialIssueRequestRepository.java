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

public interface MaterialIssueRequestRepository extends JpaRepository<MaterialIssueRequest, Long>, JpaSpecificationExecutor<MaterialIssueRequest> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT mir FROM MaterialIssueRequest mir WHERE mir.id = :id")
    Optional<MaterialIssueRequest> findByIdForUpdate(@Param("id") Long id);

    List<MaterialIssueRequest> findByProductionRequestId(Long productionRequestId);

    List<MaterialIssueRequest> findByProductionRequestIdAndStatusIn(Long productionRequestId, Collection<MaterialIssueRequestStatus> statuses);

    boolean existsByProductionRequestIdAndStatus(Long productionRequestId, MaterialIssueRequestStatus status);
}
