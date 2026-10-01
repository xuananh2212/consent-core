package vn.com.fis.consentcore.policy.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel;
import vn.com.fis.consentcore.registry.domain.model.EvidencePolicy;

/** Stable policy input assembled by the Registry application layer. */
public record RegistrationPolicyRequest(
        String tenantId,
        String consentType,
        String subjectId,
        String clientId,
        String sourceSystem,
        AcquisitionChannel acquisitionChannel,
        EvidencePolicy requestedEvidencePolicy,
        boolean requestedTrustedAutoAuthorization,
        Instant validFrom,
        Instant validUntil,
        List<String> resourceTypes,
        Map<String, Object> riskContext
) {
    public RegistrationPolicyRequest {
        resourceTypes = resourceTypes == null ? List.of() : List.copyOf(resourceTypes);
        riskContext = riskContext == null ? Map.of() : Map.copyOf(riskContext);
    }

    /** Backward-compatible constructor for callers that do not supply eligibility context. */
    public RegistrationPolicyRequest(
            String tenantId,
            String consentType,
            String subjectId,
            String clientId,
            String sourceSystem,
            AcquisitionChannel acquisitionChannel,
            EvidencePolicy requestedEvidencePolicy,
            boolean requestedTrustedAutoAuthorization,
            Instant validFrom,
            Instant validUntil) {
        this(tenantId, consentType, subjectId, clientId, sourceSystem, acquisitionChannel,
                requestedEvidencePolicy, requestedTrustedAutoAuthorization, validFrom, validUntil,
                List.of(), Map.of());
    }
}
