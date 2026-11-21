package mrp.auth;

import java.util.NoSuchElementException;
import java.util.Optional;
import mrp.auth.dto.UserView;
import mrp.auth.service.AuthService;
import mrp.auth.service.JwtService;
import mrp.security.JwtAuthenticationConverter;
import mrp.user.entity.User;
import mrp.user.entity.UserRole;
import mrp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationConverterTest {
    private final UserRepository users = mock(UserRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthService authService = mock(AuthService.class);
    private final JwtAuthenticationConverter converter = new JwtAuthenticationConverter(users, jwtService, authService);
    private final User user = new User("staff", "hash", UserRole.STAFF);
    private final Jwt token = Jwt.withTokenValue("test").header("alg", "HS256")
            .claim("uid", 1L).claim("pid", 5L).claim("role", "STAFF").claim("cv", "stamp").build();

    @BeforeEach
    void setUp() {
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.credentialVersion(user)).thenReturn("stamp");
    }

    private UserView profile(long id) {
        return new UserView(1L, "staff", UserRole.STAFF, id, "Staff", null, null, null, true, null);
    }

    @Test
    void matchingProfileIsAccepted() {
        when(authService.view(user)).thenReturn(profile(5L));
        assertEquals("staff", converter.convert(token).getName());
    }

    @Test
    void previousProfileIsRejectedEvenWithMatchingRoleAndPasswordStamp() {
        when(authService.view(user)).thenReturn(profile(6L));
        assertThrows(BadCredentialsException.class, () -> converter.convert(token));
    }

    @Test
    void profileRemovedDuringAuthenticationIsRejected() {
        when(authService.view(user)).thenThrow(new NoSuchElementException());
        assertThrows(BadCredentialsException.class, () -> converter.convert(token));
    }
}
