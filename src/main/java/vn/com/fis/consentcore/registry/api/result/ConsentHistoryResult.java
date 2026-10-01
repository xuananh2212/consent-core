package vn.com.fis.consentcore.registry.api.result;

import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.ConsentStatus;

public record ConsentHistoryResult(
        UUID transitionId,
        UUID consentId,
        String tenantId,
        ConsentStatus fromStatus,
        ConsentStatus toStatus,
        String reasonCode,
        String reasonDetail,
        String actorId,
        String actorType,
        String sourceSystem,
        String correlationId,
        String requestId,
        Instant occurredAt
) {
}
