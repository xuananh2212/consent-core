package vn.com.fis.consentcore.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import vn.com.fis.consentcore.auth.AdminAuthentication;
import vn.com.fis.consentcore.shared.api.ActorType;
import vn.com.fis.consentcore.shared.api.CommandContext;

@Component
public class RequestContextFactory {
    private final Clock clock;
    private final String securityMode;
    private final String tenantClaim;
    private final String actorTypeClaim;

    public RequestContextFactory(
            Clock clock,
            @Value("${consent.security.mode:header}") String securityMode,
            @Value("${consent.security.tenant-claim:tenant_id}") String tenantClaim,
            @Value("${consent.security.actor-type-claim:actor_type}") String actorTypeClaim) {
        this.clock = clock;
        this.securityMode = securityMode;
        this.tenantClaim = tenantClaim;
        this.actorTypeClaim = actorTypeClaim;
    }

    public CommandContext create(HttpServletRequest request, Authentication authentication) {
        Identity identity = identity(request, authentication);
        String correlationId = valueOrGenerated(request.getHeader("X-Correlation-Id"));
        String requestId = valueOrGenerated(request.getHeader("X-Request-Id"));
        return new CommandContext(identity.tenantId(), identity.actorId(), identity.actorType(),
                correlationId, requestId, identity.sourceSystem(), clock.instant());
    }

    public CommandContext create(HttpServletRequest request) {
        return create(request, null);
    }

    public String tenantId(HttpServletRequest request, Authentication authentication) {
        return identity(request, authentication).tenantId();
    }

    public String tenantId(HttpServletRequest request) {
        return tenantId(request, null);
    }

    private Identity identity(HttpServletRequest request, Authentication authentication) {
        if (authentication == null) {
            authentication = SecurityContextHolder.getContext().getAuthentication();
        }
        if (authentication instanceof AdminAuthentication admin) {
            return new Identity(admin.tenantId(), admin.actorId(), admin.actorType(), "CONSENT_ADMIN");
        }
        if ("jwt".equalsIgnoreCase(securityMode)) {
            if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
                throw new IllegalArgumentException("Authenticated JWT is required");
            }
            var jwt = jwtAuthentication.getToken();
            String tenantId = requiredClaim(jwt.getClaimAsString(tenantClaim), tenantClaim);
            String actorId = requiredClaim(jwt.getSubject(), "sub");
            ActorType actorType = parseActorType(jwt.getClaimAsString(actorTypeClaim), ActorType.USER);
            String sourceSystem = firstNonBlank(
                    jwt.getClaimAsString("azp"), jwt.getClaimAsString("client_id"), "JWT");
            return new Identity(tenantId, actorId, actorType, sourceSystem);
        }
        return new Identity(
                requiredHeader(request, "X-Tenant-Id"),
                requiredHeader(request, "X-Actor-Id"),
                parseActorType(request.getHeader("X-Actor-Type"), ActorType.SERVICE),
                valueOrDefault(request.getHeader("X-Source-System"), "REST"));
    }

    private static String requiredClaim(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required JWT claim: " + name);
        }
        return value.trim();
    }

    private static String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required header: " + name);
        }
        return value.trim();
    }

    private static ActorType parseActorType(String value, ActorType defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return ActorType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid actor type. Allowed values: USER, SERVICE, SYSTEM");
        }
    }

    private static String valueOrGenerated(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return fallback;
    }

    private record Identity(String tenantId, String actorId, ActorType actorType, String sourceSystem) { }
}
