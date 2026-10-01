package vn.com.fis.consentcore.registry.application.internal.port;

import java.time.Instant;
import java.util.UUID;

public record IdempotencyRecord(
        UUID id,
        String tenantId,
        String idempotencyKey,
        String operation,
        String requestHash,
        UUID consentId,
        Instant createdAt,
        Instant expiresAt
) {
}
