package vn.com.fis.consentcore.evidence.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.shared.domain.DomainEvent;

public record EvidenceArtifactSupersededEvent(
        UUID eventId,
        String tenantId,
        UUID aggregateId,
        UUID bundleId,
        UUID supersededArtifactId,
        UUID replacementArtifactId,
        Instant occurredAt
) implements DomainEvent {
    @Override public String eventType() { return "EvidenceArtifactSuperseded"; }
    @Override public int eventVersion() { return 1; }
}
