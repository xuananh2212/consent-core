package vn.com.fis.consentcore.registry.domain.event;

import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.EvidenceStatus;
import vn.com.fis.consentcore.shared.domain.DomainEvent;

public record ConsentEvidenceStatusChangedEvent(
        UUID eventId,
        String tenantId,
        UUID aggregateId,
        UUID evidenceBundleId,
        EvidenceStatus evidenceStatus,
        String actorId,
        Instant occurredAt
) implements DomainEvent {
    @Override public String eventType() { return "ConsentEvidenceStatusChanged"; }
    @Override public int eventVersion() { return 1; }
}
