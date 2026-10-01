package vn.com.fis.consentcore.registry.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.registry.application.internal.port.IdempotencyRecord;

@Entity
@Table(name = "consent_idempotency")
class ConsentIdempotencyJpaEntity {
    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    String tenantId;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    String idempotencyKey;

    @Column(name = "operation", nullable = false, length = 100)
    String operation;

    @Column(name = "request_hash", nullable = false, length = 64)
    String requestHash;

    @Column(name = "consent_id", nullable = false)
    UUID consentId;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    protected ConsentIdempotencyJpaEntity() {
    }

    static ConsentIdempotencyJpaEntity from(IdempotencyRecord record) {
        ConsentIdempotencyJpaEntity entity = new ConsentIdempotencyJpaEntity();
        entity.id = record.id();
        entity.tenantId = record.tenantId();
        entity.idempotencyKey = record.idempotencyKey();
        entity.operation = record.operation();
        entity.requestHash = record.requestHash();
        entity.consentId = record.consentId();
        entity.createdAt = record.createdAt();
        entity.expiresAt = record.expiresAt();
        return entity;
    }

    IdempotencyRecord toRecord() {
        return new IdempotencyRecord(id, tenantId, idempotencyKey, operation, requestHash, consentId, createdAt, expiresAt);
    }
}
