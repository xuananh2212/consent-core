package vn.com.fis.consentcore.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;
import vn.com.fis.consentcore.shared.api.ActorType;

@Component
class AdminTokenService {
    private final AdminAuthProperties properties;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    AdminTokenService(AdminAuthProperties properties) {
        this.properties = properties;
        SecretKey key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    String accessToken(AdminUser user, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.username())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("tenant_id", user.tenantId())
                .claim("actor_id", user.actorId())
                .claim("actor_type", user.actorType().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    long accessTokenSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    Instant refreshExpiresAt(Instant issuedAt) {
        return issuedAt.plus(properties.refreshTokenTtl());
    }

    /** Chỉ chấp nhận token do Consent Core ký. Token của hệ thống khác bị từ chối. */
    AdminAuthentication authenticate(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            if (!properties.issuer().equals(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())) {
                throw new AuthRejectedException("Access token không hợp lệ hoặc đã hết hạn");
            }
            String actorType = jwt.getClaimAsString("actor_type");
            return new AdminAuthentication(
                    jwt.getSubject(),
                    required(jwt.getClaimAsString("tenant_id")),
                    required(jwt.getClaimAsString("actor_id")),
                    actorType == null ? ActorType.USER : ActorType.valueOf(actorType));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AuthRejectedException("Access token không hợp lệ hoặc đã hết hạn");
        }
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new AuthRejectedException("Access token không hợp lệ hoặc đã hết hạn");
        }
        return value;
    }
}
