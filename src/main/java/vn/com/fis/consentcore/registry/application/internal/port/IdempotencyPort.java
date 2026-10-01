package vn.com.fis.consentcore.registry.application.internal.port;

import java.util.Optional;

public interface IdempotencyPort {
    void acquireTransactionLock(String tenantId, String idempotencyKey, String operation);
    Optional<IdempotencyRecord> find(String tenantId, String idempotencyKey, String operation);
    void save(IdempotencyRecord record);
}
