package vn.com.fis.consentcore.registry.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataConsentIdempotencyRepository extends JpaRepository<ConsentIdempotencyJpaEntity, UUID> {
    Optional<ConsentIdempotencyJpaEntity> findByTenantIdAndIdempotencyKeyAndOperation(
            String tenantId, String idempotencyKey, String operation);
}
