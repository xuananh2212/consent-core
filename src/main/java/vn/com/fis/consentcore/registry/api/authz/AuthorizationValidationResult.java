package vn.com.fis.consentcore.registry.api.authz;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthorizationValidationResult(
        boolean valid,
        UUID consentId,
        String status,
        UUID decisionId,
        long consentVersion,
        Integer revisionNo,
        String contentHash,
        String subjectRef,
        String clientId,
        String authenticationIssuer,
        Instant validatedAt,
        List<String> reasonCodes
) {
    public AuthorizationValidationResult {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
