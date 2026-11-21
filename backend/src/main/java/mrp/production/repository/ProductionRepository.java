package mrp.production.repository;


import mrp.production.entity.Production;
import mrp.production.entity.ProductionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionRepository extends JpaRepository<Production, Long>, JpaSpecificationExecutor<Production> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Production p WHERE p.id = :id")
    Optional<Production> findByIdForUpdate(@Param("id") Long id);

    List<Production> findByProductionRequestId(Long productionRequestId);

    List<Production> findByProductionRequestIdAndStatus(Long productionRequestId, ProductionStatus status);

    List<Production> findByProductionRequestIdAndStatusNot(Long productionRequestId, ProductionStatus status);
}