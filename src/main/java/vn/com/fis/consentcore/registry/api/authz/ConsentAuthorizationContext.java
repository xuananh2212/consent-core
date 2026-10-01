package vn.com.fis.consentcore.registry.api.authz;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.com.fis.consentcore.policy.api.RequiredAssurance;

/** Authoritative Core facts used by Flow Manager for routing and transaction binding. */
public record ConsentAuthorizationContext(
        UUID consentId,
        String status,
        String consentType,
        String requestingClientId,
        String subjectRef,
        Integer currentRevisionNo,
        String currentContentHash,
        long consentVersion,
        Instant validUntil,
        String evidencePolicy,
        String evidenceStatus,
        RequiredAssurance requiredAssurance,
        List<String> permissionCodes,
        List<String> resourceTypes,
        boolean presentationReady
) {
    public ConsentAuthorizationContext {
        permissionCodes = permissionCodes == null ? List.of() : List.copyOf(permissionCodes);
        resourceTypes = resourceTypes == null ? List.of() : List.copyOf(resourceTypes);
    }
}
