package vn.com.fis.consentcore.registry.domain.event;

import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.shared.domain.DomainEvent;

public record ConsentDecisionCapturedEvent(
        UUID eventId,
        String tenantId,
        UUID aggregateId,
        UUID decisionId,
        UUID revisionId,
        String outcome,
        String actorId,
        Instant occurredAt
) implements DomainEvent {
    @Override public String eventType() { return "ConsentDecisionCaptured"; }
    @Override public int eventVersion() { return 1; }
}
