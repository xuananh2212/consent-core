package vn.com.fis.consentcore.registry.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConsentTransition(
        UUID transitionId,
        UUID consentId,
        String tenantId,
        ConsentStatus fromStatus,
        ConsentStatus toStatus,
        String reasonCode,
        String reasonDetail,
        Instant occurredAt
) {
    public ConsentTransition {
        transitionId = Objects.requireNonNull(transitionId, "transitionId must not be null");
        consentId = Objects.requireNonNull(consentId, "consentId must not be null");
        tenantId = requireText(tenantId, "tenantId");
        toStatus = Objects.requireNonNull(toStatus, "toStatus must not be null");
        reasonCode = requireText(reasonCode, "reasonCode");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
