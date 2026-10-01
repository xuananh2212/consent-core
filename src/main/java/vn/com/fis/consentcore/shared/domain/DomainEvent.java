package vn.com.fis.consentcore.shared.domain;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();
    String tenantId();
    UUID aggregateId();
    String eventType();
    int eventVersion();
    Instant occurredAt();
}
