package mrp.catalog.repository;

import mrp.catalog.entity.Material;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface MaterialRepository extends JpaRepository<Material, Long>, JpaSpecificationExecutor<Material> {
    boolean existsByCodeAndIdNot(String code, Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Material m where m.id = :id")
    Optional<Material> findByIdForUpdate(@Param("id") Long id);
}