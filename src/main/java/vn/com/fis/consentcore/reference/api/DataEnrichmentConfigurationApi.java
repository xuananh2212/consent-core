package vn.com.fis.consentcore.reference.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.com.fis.consentcore.shared.api.CommandContext;

/** Reference/configuration contract for configuration-driven backend data enrichment. */
public interface DataEnrichmentConfigurationApi {
    UUID upsertRequirement(DataRequirementDefinition definition, CommandContext context);
    UUID upsertProvider(ProviderDefinition definition, CommandContext context);
    UUID upsertBinding(ProviderBindingDefinition definition, CommandContext context);
    UUID upsertMappingProfile(MappingProfileDefinition definition, CommandContext context);

    List<DataRequirementDefinition> listRequirements(String tenantId, String consentTypeCode, String resolutionPhase);
    List<ProviderDefinition> listProviders(String tenantId);
    List<ProviderBindingDefinition> listBindings(String tenantId);
    List<MappingProfileDefinition> listMappingProfiles(String tenantId);

    /**
     * Resolve the effective requirement set for a trusted consent-preparation context.
     * tenantId + consentType are mandatory selectors; clientId and OAuth scopes are deliberately the only
     * optional selectors in the v0.5 baseline. Configured requiredScopes use ALL/subset semantics.
     */
    List<DataRequirementDefinition> getEffectiveRequirements(
            String tenantId,
            String consentTypeCode,
            String clientId,
            List<String> effectiveScopes,
            String resolutionPhase);

    /** Backward-compatible convenience for callers that do not use client/scope selectors. */
    default List<DataRequirementDefinition> getEffectiveRequirements(
            String tenantId, String consentTypeCode, String resolutionPhase) {
        return getEffectiveRequirements(tenantId, consentTypeCode, null, List.of(), resolutionPhase);
    }

    Optional<ProviderBindingDefinition> resolveProviderBinding(
            String tenantId, String consentTypeCode, String dataType);
    Optional<ProviderDefinition> getProvider(String tenantId, String providerCode);
    Optional<MappingProfileDefinition> getMappingProfile(String tenantId, String mappingProfileCode);

    record DataRequirementDefinition(
            String consentTypeCode,
            String requirementCode,
            String resolutionPhase,
            String clientId,
            List<String> requiredScopes,
            String dataType,
            String dataPurpose,
            boolean required,
            int schemaVersion,
            Integer freshnessSeconds,
            Map<String, Object> configuration,
            boolean active) {
        public DataRequirementDefinition {
            clientId = normalizeNullable(clientId);
            requiredScopes = normalizeScopes(requiredScopes);
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        }

        /** Backward-compatible constructor for pre-v0.5 configuration without client/scope selectors. */
        public DataRequirementDefinition(
                String consentTypeCode,
                String requirementCode,
                String resolutionPhase,
                String dataType,
                String dataPurpose,
                boolean required,
                int schemaVersion,
                Integer freshnessSeconds,
                Map<String, Object> configuration,
                boolean active) {
            this(consentTypeCode, requirementCode, resolutionPhase, null, List.of(), dataType, dataPurpose,
                    required, schemaVersion, freshnessSeconds, configuration, active);
        }

        private static String normalizeNullable(String value) {
            return value == null || value.isBlank() || "*".equals(value.trim()) ? null : value.trim();
        }

        private static List<String> normalizeScopes(List<String> scopes) {
            if (scopes == null || scopes.isEmpty()) return List.of();
            return scopes.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .sorted()
                    .toList();
        }
    }

    record ProviderDefinition(
            String providerCode,
            String providerType,
            String adapterKey,
            String endpointTemplate,
            String httpMethod,
            String credentialReference,
            int timeoutMs,
            Map<String, Object> configuration,
            boolean active) {
        public ProviderDefinition {
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        }
    }

    record ProviderBindingDefinition(
            String dataType,
            String consentTypeCode,
            String providerCode,
            String mappingProfileCode,
            int priority,
            Map<String, Object> configuration,
            boolean active) {
        public ProviderBindingDefinition {
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        }
    }

    record MappingProfileDefinition(
            String mappingProfileCode,
            String dataType,
            String providerCode,
            int schemaVersion,
            Map<String, Object> configuration,
            boolean active) {
        public MappingProfileDefinition {
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        }
    }
}
