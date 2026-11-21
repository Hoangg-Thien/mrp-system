package mrp.fgissue.repository;


import mrp.fgissue.entity.FinishedGoodsIssueRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinishedGoodsIssueRequestRepository extends JpaRepository<FinishedGoodsIssueRequest, Long>, JpaSpecificationExecutor<FinishedGoodsIssueRequest> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM FinishedGoodsIssueRequest r WHERE r.id = :id")
    Optional<FinishedGoodsIssueRequest> findByIdForUpdate(@Param("id") Long id);

    boolean existsByRequestedBy(Long requestedBy);

    boolean existsByReviewedBy(Long reviewedBy);
}