package mrp.security;


import mrp.common.InvalidCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Lấy {@code uid} (users.id) và {@code pid} (id bảng profile) từ token đã được
 * {@link JwtAuthenticationConverter} kiểm tra lại với DB ở mỗi request.
 */
@Component
public class CurrentUserResolver {
    public CurrentUser resolve(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt && jwt.getToken() != null) {
            Long userId = toLong(jwt.getToken().getClaim("uid"));
            Long profileId = toLong(jwt.getToken().getClaim("pid"));
            if (userId != null && profileId != null) {
                return new CurrentUser(userId, profileId, authentication.getName());
            }
        }
        throw new InvalidCredentialsException("Token không hợp lệ");
    }

    /** Claim số có thể được parse thành Integer hoặc Long tùy độ lớn. */
    private static Long toLong(Object value) { return value instanceof Number number ? number.longValue() : null; }
}