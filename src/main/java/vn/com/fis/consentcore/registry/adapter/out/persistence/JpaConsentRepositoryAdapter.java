package vn.com.fis.consentcore.registry.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import vn.com.fis.consentcore.registry.application.internal.port.ConsentRepositoryPort;
import vn.com.fis.consentcore.registry.domain.model.Consent;

@Repository
class JpaConsentRepositoryAdapter implements ConsentRepositoryPort {
    private final SpringDataConsentRepository repository;

    JpaConsentRepositoryAdapter(SpringDataConsentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Consent save(Consent consent) {
        ConsentJpaEntity entity = repository.findByTenantIdAndId(consent.tenantId(), consent.id())
                .orElseGet(() -> ConsentJpaEntity.fromDomain(consent));
        entity.apply(consent);
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<Consent> findById(String tenantId, UUID consentId) {
        return repository.findByTenantIdAndId(tenantId, consentId).map(ConsentJpaEntity::toDomain);
    }

    @Override
    public Optional<Consent> findByExternalReference(
            String tenantId,
            String sourceSystem,
            String externalConsentId
    ) {
        return repository.findByTenantIdAndSourceSystemAndExternalConsentId(
                tenantId, sourceSystem, externalConsentId).map(ConsentJpaEntity::toDomain);
    }

    @Override
    public List<Consent> findExpiryCandidates(Instant now, int limit) {
        return repository.findExpiryCandidates(now, PageRequest.of(0, Math.max(1, limit)))
                .stream().map(ConsentJpaEntity::toDomain).toList();
    }
}

