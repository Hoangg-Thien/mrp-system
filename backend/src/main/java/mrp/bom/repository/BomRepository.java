package mrp.bom.repository;


import mrp.bom.entity.Bom;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BomRepository extends JpaRepository<Bom, Long>, JpaSpecificationExecutor<Bom> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bom b WHERE b.id = :id")
    Optional<Bom> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT MAX(b.version) FROM Bom b WHERE b.productId = :productId")
    Integer findMaxVersionByProductId(@Param("productId") Long productId);

    @Query("SELECT b FROM Bom b WHERE b.productId = :productId AND b.active = true")
    Optional<Bom> findActiveByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE Bom b SET b.active = false WHERE b.productId = :productId AND b.active = true")
    int deactivateAllByProductId(@Param("productId") Long productId);

    boolean existsByProductId(Long productId);
}