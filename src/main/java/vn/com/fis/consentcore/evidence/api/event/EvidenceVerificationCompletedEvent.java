package vn.com.fis.consentcore.evidence.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.shared.domain.DomainEvent;

public record EvidenceVerificationCompletedEvent(
        UUID eventId,
        String tenantId,
        UUID aggregateId,
        UUID bundleId,
        UUID artifactId,
        UUID verificationId,
        String result,
        String verificationMethod,
        Instant occurredAt
) implements DomainEvent {
    @Override public String eventType() { return "EvidenceVerificationCompleted"; }
    @Override public int eventVersion() { return 1; }
}
