package mrp.user.repository;

import mrp.user.dto.StaffView;
import mrp.user.entity.Staff;
import mrp.user.entity.User;
import mrp.user.entity.UserRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByUserId(Long userId);
    void deleteByUserId(Long userId);
    @org.springframework.data.jpa.repository.Query("""
        select new mrp.user.dto.StaffView(s.id, s.fullName) from Staff s, User u
        where s.userId = u.id and u.active = true and u.role = mrp.user.entity.UserRole.STAFF
        order by s.fullName, s.id
        """)
    java.util.List<StaffView> findActiveStaff();
}