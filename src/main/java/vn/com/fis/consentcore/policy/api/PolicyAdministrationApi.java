package vn.com.fis.consentcore.policy.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel;
import vn.com.fis.consentcore.registry.domain.model.EvidencePolicy;
import vn.com.fis.consentcore.shared.api.CommandContext;

public interface PolicyAdministrationApi {
    UUID upsertConsentTypePolicy(ConsentTypePolicyCommand command);
    UUID upsertTrustedSource(TrustedSourceCommand command);
    UUID publishPolicyDefinition(PolicyDefinitionCommand command);
    List<PolicyDefinition> listPolicyDefinitions(String tenantId, String policyType);

    record ConsentTypePolicyCommand(
            String consentType,
            EvidencePolicy evidencePolicy,
            int maxValidityDays,
            boolean requireSubject,
            boolean allowTrustedAutoAuthorization,
            List<AcquisitionChannel> allowedChannels,
            CommandContext context) {
        public ConsentTypePolicyCommand {
            allowedChannels = allowedChannels == null ? List.of() : List.copyOf(allowedChannels);
        }
    }

    record TrustedSourceCommand(
            String sourceSystem,
            String clientId,
            List<String> allowedConsentTypes,
            Instant validFrom,
            Instant validUntil,
            CommandContext context) {
        public TrustedSourceCommand {
            allowedConsentTypes = allowedConsentTypes == null ? List.of() : List.copyOf(allowedConsentTypes);
        }
    }

    record PolicyDefinitionCommand(
            String policyCode,
            int policyVersion,
            String policyType,
            String status,
            Map<String, Object> definition,
            Instant effectiveFrom,
            Instant effectiveUntil,
            CommandContext context) {
        public PolicyDefinitionCommand {
            definition = definition == null ? Map.of() : Map.copyOf(definition);
        }
    }

    record PolicyDefinition(
            UUID id,
            String policyCode,
            int policyVersion,
            String policyType,
            String status,
            Map<String, Object> definition,
            Instant effectiveFrom,
            Instant effectiveUntil,
            Instant createdAt,
            String createdBy) {
    }
}
