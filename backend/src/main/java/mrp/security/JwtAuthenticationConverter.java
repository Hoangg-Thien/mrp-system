package mrp.security;


import mrp.user.entity.User;
import mrp.user.repository.UserRepository;
import mrp.auth.service.AuthService;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {
    private final UserRepository users;
    private final mrp.auth.service.JwtService jwtService;
    private final AuthService authService;
    public JwtAuthenticationConverter(UserRepository users, mrp.auth.service.JwtService jwtService, AuthService authService) {
        this.users = users; this.jwtService = jwtService; this.authService = authService;
    }

    @Override
    public JwtAuthenticationToken convert(Jwt token) {
        Long userId = toLong(token.getClaim("uid"));
        Long profileId = toLong(token.getClaim("pid"));
        if (userId == null || profileId == null || token.getClaimAsString("role") == null) {
            throw new BadCredentialsException("Token không hợp lệ");
        }
        User user = users.findById(userId).orElseThrow(() -> new BadCredentialsException("Token không hợp lệ"));
        if (!user.isActive() || !user.getRole().name().equals(token.getClaimAsString("role"))) {
            throw new BadCredentialsException("Tài khoản không hoạt động hoặc token đã cũ");
        }
        String stamp = token.getClaimAsString("cv");
        if (stamp == null || !java.security.MessageDigest.isEqual(
                jwtService.credentialVersion(user).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                stamp.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new BadCredentialsException("Mật khẩu đã thay đổi hoặc token đã cũ. Vui lòng đăng nhập lại");
        }
        try {
            if (!profileId.equals(authService.view(user).profileId())) {
                throw new BadCredentialsException("Hồ sơ vai trò đã thay đổi. Vui lòng đăng nhập lại");
            }
        } catch (NoSuchElementException ex) {
            throw new BadCredentialsException("Hồ sơ vai trò đã thay đổi. Vui lòng đăng nhập lại", ex);
        }
        return new JwtAuthenticationToken(token,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                user.getUsername());
    }

    /** Claim số có thể được parse thành Integer hoặc Long tùy độ lớn. */
    private static Long toLong(Object value) { return value instanceof Number number ? number.longValue() : null; }
}
