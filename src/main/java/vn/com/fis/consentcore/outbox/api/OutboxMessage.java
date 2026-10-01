package vn.com.fis.consentcore.outbox.api;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(
        UUID id,
        String tenantId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        int eventVersion,
        String payload,
        String correlationId,
        Instant occurredAt,
        int attempts
) {
}
