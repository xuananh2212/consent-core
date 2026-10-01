package vn.com.fis.consentcore.policy.api;

import java.util.List;
import java.util.UUID;
import vn.com.fis.consentcore.enrichment.api.ResolvedRequirementData;
import vn.com.fis.consentcore.shared.api.CommandContext;

/**
 * Policy boundary for post-authentication business eligibility during consent preparation.
 * Flow Manager decides when preparation runs; Consent Core owns this ALLOW/DENY business decision.
 */
public interface PreparationEligibilityPolicyApi {

    PreparationEligibilityDecision evaluate(PreparationEligibilityRequest request);

    record PreparationEligibilityRequest(
            UUID consentId,
            String consentType,
            String clientId,
            String subjectRef,
            List<String> effectiveScopes,
            List<ResolvedRequirementData> policyFacts,
            CommandContext context) {
        public PreparationEligibilityRequest {
            effectiveScopes = effectiveScopes == null ? List.of() : List.copyOf(effectiveScopes);
            policyFacts = policyFacts == null ? List.of() : List.copyOf(policyFacts);
        }
    }

    record PreparationEligibilityDecision(
            boolean allowed,
            String reasonCode,
            String messageKey,
            boolean retryable,
            String deniedRequirementCode) {

        public static PreparationEligibilityDecision allow() {
            return new PreparationEligibilityDecision(true, null, null, false, null);
        }

        public static PreparationEligibilityDecision deny(
                String reasonCode, String messageKey, boolean retryable, String deniedRequirementCode) {
            return new PreparationEligibilityDecision(false, reasonCode, messageKey, retryable, deniedRequirementCode);
        }
    }
}
