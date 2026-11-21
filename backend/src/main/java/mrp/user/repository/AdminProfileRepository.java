package mrp.user.repository;

import mrp.user.entity.AdminProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AdminProfileRepository extends JpaRepository<AdminProfile, Long> { Optional<AdminProfile> findByUserId(Long userId); void deleteByUserId(Long userId); }