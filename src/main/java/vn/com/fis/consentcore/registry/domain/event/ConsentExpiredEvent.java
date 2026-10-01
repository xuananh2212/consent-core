package vn.com.fis.consentcore.registry.domain.event;

import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.shared.domain.DomainEvent;

public record ConsentExpiredEvent(
        UUID eventId,
        String tenantId,
        UUID aggregateId,
        String actorId,
        Instant occurredAt
) implements DomainEvent {
    @Override public String eventType() { return "ConsentExpired"; }
    @Override public int eventVersion() { return 1; }
}
