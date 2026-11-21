package mrp.auth.service;


import mrp.user.entity.User;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final long expirationSeconds;
    private final javax.crypto.SecretKey secret;

    public JwtService(JwtEncoder encoder, @Value("${app.jwt.expiration-seconds:86400}") long expirationSeconds, javax.crypto.SecretKey secret) {
        this.encoder = encoder;
        this.expirationSeconds = expirationSeconds;
        this.secret = secret;
    }

    public String createToken(User user, Long profileId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("mrp-system")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .subject(user.getUsername())
                .claims(values -> values.putAll(Map.of(
                        "uid", user.getId(),
                        "pid", profileId,
                        "role", user.getRole().name(),
                        "cv", credentialVersion(user))))
                .build();
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    public long getExpirationSeconds() { return expirationSeconds; }

    /** Keyed stamp revokes every existing token when the stored password changes. */
    public String credentialVersion(User user) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(secret);
            byte[] stamp = mac.doFinal((user.getId() + ":" + user.getPasswordHash())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(stamp);
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Không thể kiểm tra phiên đăng nhập", ex);
        }
    }
}
