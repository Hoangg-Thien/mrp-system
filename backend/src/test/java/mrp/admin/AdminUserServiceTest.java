package mrp.admin;


import mrp.admin.dto.ChangeRoleRequest;
import mrp.admin.dto.CreateUserRequest;
import mrp.admin.service.AdminUserService;
import mrp.auth.dto.UserView;
import mrp.auth.service.AuthService;
import mrp.common.exception.BusinessException;
import mrp.user.entity.Staff;
import mrp.user.entity.User;
import mrp.user.entity.UserRole;
import mrp.user.repository.AdminProfileRepository;
import mrp.user.repository.StaffRepository;
import mrp.user.repository.UserRepository;
import mrp.user.repository.WarehouseManagerRepository;
import mrp.user.service.UserReferenceChecker;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock UserRepository users;
    @Mock StaffRepository staff;
    @Mock WarehouseManagerRepository managers;
    @Mock AdminProfileRepository admins;
    @Mock PasswordEncoder encoder;
    @Mock AuthService authService;
    @Mock UserReferenceChecker referenceChecker;
    @Mock Authentication actor;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(users, staff, managers, admins, encoder, authService, referenceChecker);
        lenient().when(actor.getName()).thenReturn("root");
    }

    @Test
    void acc01_createStaffCreatesPasswordHashAndProfile() {
        User user = new User("staff01", "hash", UserRole.STAFF);
        when(users.existsByUsername("staff01")).thenReturn(false);
        when(encoder.encode("Password@123")).thenReturn("hash");
        when(users.save(any(User.class))).thenReturn(user);
        when(authService.view(user)).thenReturn(new UserView(1L, "staff01", UserRole.STAFF, 2L, "Staff", null, null, null, true, null));

        UserView result = service.create(new CreateUserRequest("staff01", "Password@123", UserRole.STAFF, "Staff", null, null, null));

        verify(staff).save(any(Staff.class));
        assertEquals(UserRole.STAFF, result.role());
    }

    @Test
    void acc02_duplicateUsernameIsConflict() {
        when(users.existsByUsername("root")).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.create(new CreateUserRequest("root", "Password@123", UserRole.ADMIN, "Root", null, null, null)));
    }

    @Test
    void acc03_cannotLockSelf() {
        User user = new User("root", "hash", UserRole.ADMIN);
        when(users.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(user));
        assertThrows(BusinessException.class, () -> service.lock(1L, actor));
    }

    @Test
    void acc04_cannotRemoveLastActiveAdmin() {
        User user = new User("other", "hash", UserRole.ADMIN);
        when(users.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(user));
        when(users.countByRoleAndActive(UserRole.ADMIN, true)).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.lock(1L, actor));
        assertThrows(BusinessException.class, () -> service.delete(1L, actor));
    }

    @Test
    void acc05_referencedUserCannotChangeRoleOrDelete() {
        User user = new User("staff", "hash", UserRole.STAFF);
        UserView profile = new UserView(1L, "staff", UserRole.STAFF, 2L, "Staff", null, null, null, true, null);
        when(users.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(user));
        when(authService.view(user)).thenReturn(profile);
        when(referenceChecker.isReferenced(user, 2L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.changeRole(1L, new ChangeRoleRequest(UserRole.WAREHOUSE_MANAGER), actor));
        assertThrows(BusinessException.class, () -> service.delete(1L, actor));
    }

    @Test
    void acc06_unlockRestoresActiveState() {
        User user = new User("staff", "hash", UserRole.STAFF);
        user.setActive(false);
        when(users.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(user));
        when(authService.view(user)).thenReturn(new UserView(1L, "staff", UserRole.STAFF, 2L, "Staff", null, null, null, true, null));
        UserView result = service.unlock(1L);
        assertEquals(true, user.isActive());
        assertEquals(true, result.active());
    }

    @Test
    void acc07_invalidPageSizeIsConflict() {
        assertThrows(BusinessException.class, () -> service.list(null, null, null, 0, 101, "username", "asc"));
    }
}