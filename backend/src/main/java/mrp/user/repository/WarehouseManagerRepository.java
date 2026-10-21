package mrp.user.repository;

import mrp.user.entity.WarehouseManager;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WarehouseManagerRepository extends JpaRepository<WarehouseManager, Long> { Optional<WarehouseManager> findByUserId(Long userId); void deleteByUserId(Long userId); }