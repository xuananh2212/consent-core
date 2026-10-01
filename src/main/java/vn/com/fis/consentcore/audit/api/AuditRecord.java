package vn.com.fis.consentcore.audit.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.shared.api.ActorType;

public record AuditRecord(
        UUID id,
        String tenantId,
        String aggregateType,
        UUID aggregateId,
        String action,
        String result,
        String actorId,
        ActorType actorType,
        String sourceSystem,
        String correlationId,
        String requestId,
        Map<String, Object> details,
        Instant occurredAt
) {
    public AuditRecord {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
