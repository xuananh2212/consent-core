package vn.com.fis.consentcore.registry.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.ConsentStatus;
import vn.com.fis.consentcore.registry.domain.model.ConsentTransition;
import vn.com.fis.consentcore.shared.api.CommandContext;

@Entity
@Table(name = "consent_status_history")
class ConsentStatusHistoryJpaEntity {
    @Id
    UUID id;

    @Column(name = "sequence_no", nullable = false, insertable = false, updatable = false)
    Long sequenceNo;

    @Column(name = "consent_id", nullable = false)
    UUID consentId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 60)
    ConsentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 60)
    ConsentStatus toStatus;

    @Column(name = "reason_code", nullable = false, length = 100)
    String reasonCode;

    @Column(name = "reason_detail", length = 1000)
    String reasonDetail;

    @Column(name = "actor_id", nullable = false, length = 200)
    String actorId;

    @Column(name = "actor_type", nullable = false, length = 40)
    String actorType;

    @Column(name = "source_system", nullable = false, length = 100)
    String sourceSystem;

    @Column(name = "correlation_id", nullable = false, length = 100)
    String correlationId;

    @Column(name = "request_id", nullable = false, length = 100)
    String requestId;

    @Column(name = "occurred_at", nullable = false)
    Instant occurredAt;

    protected ConsentStatusHistoryJpaEntity() {
    }

    static ConsentStatusHistoryJpaEntity from(ConsentTransition transition, CommandContext context) {
        ConsentStatusHistoryJpaEntity entity = new ConsentStatusHistoryJpaEntity();
        entity.id = transition.transitionId();
        entity.consentId = transition.consentId();
        entity.tenantId = transition.tenantId();
        entity.fromStatus = transition.fromStatus();
        entity.toStatus = transition.toStatus();
        entity.reasonCode = transition.reasonCode();
        entity.reasonDetail = transition.reasonDetail();
        entity.actorId = context.actorId();
        entity.actorType = context.actorType().name();
        entity.sourceSystem = context.sourceSystem();
        entity.correlationId = context.correlationId();
        entity.requestId = context.requestId();
        entity.occurredAt = transition.occurredAt();
        return entity;
    }
}
