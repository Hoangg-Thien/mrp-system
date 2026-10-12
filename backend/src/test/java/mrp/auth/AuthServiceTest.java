package mrp.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import mrp.common.InvalidCredentialsException;
import mrp.user.AdminProfileRepository;
import mrp.user.StaffRepository;
import mrp.user.User;
import mrp.user.UserRepository;
import mrp.user.UserRole;
import mrp.user.WarehouseManagerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock JwtService jwtService;
    @Mock StaffRepository staff;
    @Mock WarehouseManagerRepository managers;
    @Mock AdminProfileRepository admins;

    private AuthService service;
    private BCryptPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder();
        service = new AuthService(users, encoder, jwtService, staff, managers, admins);
    }

    @Test
    void auth01_loginReturnsTokenAndCurrentProfile() {
        User user = new User("admin", encoder.encode("Password@123"), UserRole.ADMIN);
        when(users.findByUsername("admin")).thenReturn(java.util.Optional.of(user));
        when(admins.findByUserId(null)).thenReturn(java.util.Optional.of(new mrp.user.AdminProfile(1L, "Admin", null, null, null)));
        when(jwtService.createToken(user, null)).thenReturn("jwt");
        when(jwtService.getExpirationSeconds()).thenReturn(86400L);

        LoginResponse response = service.login(new LoginRequest("admin", "Password@123"));

        assertEquals("jwt", response.accessToken());
        assertEquals(UserRole.ADMIN, response.user().role());
    }

    @Test
    void auth02_unknownUsernameIsRejectedWithSameGenericError() {
        when(users.findByUsername("missing")).thenReturn(java.util.Optional.empty());
        InvalidCredentialsException error = assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("missing", "Password@123")));
        assertEquals("Username hoặc mật khẩu không đúng", error.getMessage());
    }

    @Test
    void auth03_wrongPasswordUsesGenericUnauthorizedError() {
        User user = new User("admin", encoder.encode("Password@123"), UserRole.ADMIN);
        when(users.findByUsername("admin")).thenReturn(java.util.Optional.of(user));

        InvalidCredentialsException error = assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("admin", "wrong-password")));

        assertEquals("Username hoặc mật khẩu không đúng", error.getMessage());
    }
}