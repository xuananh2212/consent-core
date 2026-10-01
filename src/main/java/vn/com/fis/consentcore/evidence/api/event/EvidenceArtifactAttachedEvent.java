package vn.com.fis.consentcore.evidence.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.shared.domain.DomainEvent;

public record EvidenceArtifactAttachedEvent(
        UUID eventId,
        String tenantId,
        UUID aggregateId,
        UUID bundleId,
        UUID artifactId,
        UUID revisionId,
        String evidenceType,
        String sha256,
        long sizeBytes,
        Instant occurredAt
) implements DomainEvent {
    @Override public String eventType() { return "EvidenceArtifactAttached"; }
    @Override public int eventVersion() { return 1; }
}
