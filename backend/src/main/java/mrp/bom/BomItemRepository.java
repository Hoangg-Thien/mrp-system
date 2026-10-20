package mrp.bom;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BomItemRepository extends JpaRepository<BomItem, Long> {

    List<BomItem> findByBomIdOrderByMaterialIdAsc(Long bomId);

    List<BomItem> findByBomIdIn(Collection<Long> bomIds);

    boolean existsByBomId(Long bomId);

    long countByBomId(Long bomId);
}
