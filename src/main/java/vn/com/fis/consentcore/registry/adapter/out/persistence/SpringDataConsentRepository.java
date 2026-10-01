package vn.com.fis.consentcore.registry.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataConsentRepository extends JpaRepository<ConsentJpaEntity, UUID> {
    Optional<ConsentJpaEntity> findByTenantIdAndId(String tenantId, UUID id);
    Optional<ConsentJpaEntity> findByTenantIdAndSourceSystemAndExternalConsentId(
            String tenantId, String sourceSystem, String externalConsentId);

    @Query("""
            select c from ConsentJpaEntity c
             where c.validUntil <= :now
               and c.status in (vn.com.fis.consentcore.registry.domain.model.ConsentStatus.REGISTERED,
                                vn.com.fis.consentcore.registry.domain.model.ConsentStatus.AWAITING_AUTHORIZATION,
                                vn.com.fis.consentcore.registry.domain.model.ConsentStatus.AUTHORIZED,
                                vn.com.fis.consentcore.registry.domain.model.ConsentStatus.SUSPENDED)
             order by c.validUntil, c.id
            """)
    List<ConsentJpaEntity> findExpiryCandidates(@Param("now") Instant now, Pageable pageable);
}
