package vn.com.fis.consentcore.registry.application.internal.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.Consent;

public interface ConsentRepositoryPort {
    Consent save(Consent consent);
    Optional<Consent> findById(String tenantId, UUID consentId);
    Optional<Consent> findByExternalReference(String tenantId, String sourceSystem, String externalConsentId);
    List<Consent> findExpiryCandidates(Instant now, int limit);
}
