package vn.com.fis.consentcore.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "consent.admin-auth")
public record AdminAuthProperties(
        String issuer,
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String seedUsername,
        String seedPassword
) {
}
