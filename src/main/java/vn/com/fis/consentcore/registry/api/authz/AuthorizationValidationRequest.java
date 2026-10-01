package vn.com.fis.consentcore.registry.api.authz;

import java.util.UUID;

public record AuthorizationValidationRequest(
        UUID consentId,
        String clientId,
        String subjectRef,
        Integer revisionNo,
        String contentHash,
        String sourceInteractionRef,
        String authorizationServerRef,
        String asTransactionRef
) {
}
