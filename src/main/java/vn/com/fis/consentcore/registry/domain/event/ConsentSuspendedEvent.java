package vn.com.fis.consentcore.registry.domain.event;

import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.shared.domain.DomainEvent;

public record ConsentSuspendedEvent(
        UUID eventId,
        String tenantId,
        UUID aggregateId,
        String actorId,
        String reasonCode,
        String reasonDetail,
        Instant occurredAt
) implements DomainEvent {
    @Override public String eventType() { return "ConsentSuspended"; }
    @Override public int eventVersion() { return 1; }
}
