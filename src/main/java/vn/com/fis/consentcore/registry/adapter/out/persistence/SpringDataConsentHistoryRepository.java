package vn.com.fis.consentcore.registry.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataConsentHistoryRepository extends JpaRepository<ConsentStatusHistoryJpaEntity, UUID> {
    List<ConsentStatusHistoryJpaEntity> findByTenantIdAndConsentIdOrderBySequenceNoAsc(
            String tenantId, UUID consentId);
}
