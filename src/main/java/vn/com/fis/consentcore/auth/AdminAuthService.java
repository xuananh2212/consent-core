package vn.com.fis.consentcore.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class AdminAuthService {
    private final AdminUserRepository users;
    private final AdminTokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    AdminAuthService(AdminUserRepository users, AdminTokenService tokens, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
    }

    TokenResponse login(String username, String password, Instant now) {
        AdminUser user = users.findByUsername(username)
                .filter(AdminUser::enabled)
                .filter(found -> passwordEncoder.matches(password, found.passwordHash()))
                .orElseThrow(() -> new AuthRejectedException("Sai tên đăng nhập hoặc mật khẩu."));
        return issue(user, now);
    }

    TokenResponse refresh(String refreshToken, Instant now) {
        AdminUserRepository.RefreshSession session = users.findActiveRefresh(AdminTokenService.hash(refreshToken), now)
                .filter(found -> found.user().enabled())
                .orElseThrow(() -> new AuthRejectedException("Refresh token không còn hiệu lực."));
        users.revoke(session.refreshId(), now);
        return issue(session.user(), now);
    }

    void logout(String refreshToken, Instant now) {
        users.findActiveRefresh(AdminTokenService.hash(refreshToken), now)
                .ifPresent(session -> users.revoke(session.refreshId(), now));
    }

    private TokenResponse issue(AdminUser user, Instant now) {
        String refreshToken = newRefreshToken();
        users.insertRefreshToken(
                java.util.UUID.randomUUID().toString(),
                user.id(),
                AdminTokenService.hash(refreshToken),
                tokens.refreshExpiresAt(now),
                now);
        return new TokenResponse(
                tokens.accessToken(user, now),
                refreshToken,
                tokens.accessTokenSeconds(),
                "Bearer",
                user.username(),
                user.displayName(),
                user.tenantId(),
                user.actorId(),
                user.actorType().name());
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String tokenType,
            String username,
            String displayName,
            String tenantId,
            String actorId,
            String actorType
    ) {
    }
}
