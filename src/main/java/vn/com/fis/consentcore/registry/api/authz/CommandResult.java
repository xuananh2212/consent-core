package vn.com.fis.consentcore.registry.api.authz;

import java.time.Instant;
import java.util.UUID;

public record CommandResult(
        String operation,
        String idempotencyKey,
        UUID consentId,
        String consentStatus,
        UUID decisionId,
        long consentVersion,
        Instant recordedAt
) {
}
