package mrp.user;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StaffRepository extends JpaRepository<Staff, Long> { Optional<Staff> findByUserId(Long userId); void deleteByUserId(Long userId); }