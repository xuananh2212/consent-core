package vn.com.fis.consentcore.policy.api;

import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.EvidencePolicy;

public record RegistrationPolicyDecision(
        UUID policyId,
        EvidencePolicy evidencePolicy,
        boolean trustedAutoAuthorizationAllowed,
        int maxValidityDays
) {
}
