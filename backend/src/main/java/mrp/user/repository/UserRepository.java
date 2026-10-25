package mrp.user.repository;


import mrp.user.entity.AdminProfile;
import mrp.user.entity.Staff;
import mrp.user.entity.User;
import mrp.user.entity.UserRole;
import mrp.user.entity.WarehouseManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    long countByRoleAndActive(UserRole role, boolean active);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        select u from User u
        where (:role is null or u.role = :role)
          and (:active is null or u.active = :active)
        """)
    Page<User> searchWithoutKeyword(@Param("role") UserRole role, @Param("active") Boolean active, Pageable pageable);

    @Query("""
        select u from User u
        where (:role is null or u.role = :role)
          and (:active is null or u.active = :active)
          and (lower(u.username) like lower(concat('%', :keyword, '%'))
               or exists (select 1 from Staff s where s.userId = u.id and
                          (lower(s.fullName) like lower(concat('%', :keyword, '%')) or lower(coalesce(s.email, '')) like lower(concat('%', :keyword, '%'))))
               or exists (select 1 from WarehouseManager w where w.userId = u.id and
                          (lower(w.fullName) like lower(concat('%', :keyword, '%')) or lower(coalesce(w.email, '')) like lower(concat('%', :keyword, '%'))))
               or exists (select 1 from AdminProfile a where a.userId = u.id and
                          (lower(a.fullName) like lower(concat('%', :keyword, '%')) or lower(coalesce(a.email, '')) like lower(concat('%', :keyword, '%')))))
        """)
    Page<User> searchWithKeyword(@Param("role") UserRole role, @Param("active") Boolean active,
                                 @Param("keyword") String keyword, Pageable pageable);

    default Page<User> search(UserRole role, Boolean active, String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return searchWithoutKeyword(role, active, pageable);
        }
        return searchWithKeyword(role, active, keyword.trim(), pageable);
    }
}
