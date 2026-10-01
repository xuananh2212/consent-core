package vn.com.fis.consentcore.shared.api;

import java.time.Instant;
import java.util.Objects;

public record CommandContext(
        String tenantId,
        String actorId,
        ActorType actorType,
        String correlationId,
        String requestId,
        String sourceSystem,
        Instant requestedAt
) {
    public CommandContext {
        tenantId = requireText(tenantId, "tenantId", 100);
        actorId = requireText(actorId, "actorId", 200);
        actorType = Objects.requireNonNull(actorType, "actorType must not be null");
        correlationId = requireText(correlationId, "correlationId", 100);
        requestId = requireText(requestId, "requestId", 100);
        sourceSystem = requireText(sourceSystem, "sourceSystem", 100);
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt must not be null");
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
