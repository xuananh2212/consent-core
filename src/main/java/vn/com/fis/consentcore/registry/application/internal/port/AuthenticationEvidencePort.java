package vn.com.fis.consentcore.registry.application.internal.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.com.fis.consentcore.registry.api.authz.InteractionProvenance;
import vn.com.fis.consentcore.registry.api.authz.VerifiedAuthenticationContext;

public interface AuthenticationEvidencePort {
    void append(AuthenticationEvidenceRecord record);

    Optional<AuthenticationEvidenceRecord> findMatching(
            String tenantId, UUID consentId, UUID decisionId,
            String sourceInteractionRef, String authorizationServerRef, String asTransactionRef);

    record AuthenticationEvidenceRecord(
            UUID id,
            String tenantId,
            UUID consentId,
            UUID decisionId,
            String subjectRef,
            InteractionProvenance interaction,
            VerifiedAuthenticationContext authentication,
            Instant createdAt
    ) {
    }
}
