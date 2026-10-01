package vn.com.fis.consentcore.policy.api;

import java.util.List;
import java.util.Map;

/**
 * Public SPI for organization-specific client, subject, resource and risk eligibility checks.
 * Implementations belong in downstream adapters; the Policy module owns orchestration and the decision.
 */
public interface EligibilityProvider {
    String providerName();

    EligibilityResult evaluate(RegistrationPolicyRequest request);

    record EligibilityResult(boolean eligible, List<String> reasons, Map<String, Object> attributes) {
        public EligibilityResult {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }

        public static EligibilityResult allow() {
            return new EligibilityResult(true, List.of(), Map.of());
        }

        public static EligibilityResult deny(String reason) {
            return new EligibilityResult(false, List.of(reason), Map.of());
        }
    }
}
