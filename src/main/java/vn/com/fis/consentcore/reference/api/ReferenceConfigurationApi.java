package vn.com.fis.consentcore.reference.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.shared.api.CommandContext;

/** Public module API for tenant-owned Consent Core reference data and configuration. */
public interface ReferenceConfigurationApi {
    UUID upsertConsentType(ConsentTypeDefinition definition, CommandContext context);
    UUID upsertPurpose(PurposeDefinition definition, CommandContext context);
    UUID upsertCaptureMethod(CaptureMethodDefinition definition, CommandContext context);
    UUID upsertStorageProfile(StorageProfile definition, CommandContext context);
    UUID upsertRetentionProfile(RetentionProfile definition, CommandContext context);
    void upsertTenantConfiguration(TenantConfiguration definition, CommandContext context);

    List<ConsentTypeDefinition> listConsentTypes(String tenantId);
    List<PurposeDefinition> listPurposes(String tenantId);
    List<CaptureMethodDefinition> listCaptureMethods(String tenantId);
    List<StorageProfile> listStorageProfiles(String tenantId);
    List<RetentionProfile> listRetentionProfiles(String tenantId);
    TenantConfiguration getTenantConfiguration(String tenantId);

    record ConsentTypeDefinition(
            String code, String displayName, String description, int schemaVersion,
            Map<String, Object> contentSchema, boolean active) {
        public ConsentTypeDefinition {
            contentSchema = contentSchema == null ? Map.of() : Map.copyOf(contentSchema);
        }
    }

    record PurposeDefinition(
            String code, String displayName, String description, String legalBasis,
            int schemaVersion, Map<String, Object> attributesSchema, boolean active) {
        public PurposeDefinition {
            attributesSchema = attributesSchema == null ? Map.of() : Map.copyOf(attributesSchema);
        }
    }

    record CaptureMethodDefinition(
            String code, String displayName, String description, String defaultEvidenceType,
            Map<String, Object> configuration, boolean active) {
        public CaptureMethodDefinition {
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        }
    }

    record StorageProfile(
            String code, String providerType, String encryptionKeyReference,
            Map<String, Object> configuration, boolean active) {
        public StorageProfile {
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        }
    }

    record RetentionProfile(
            String code, int consentRetentionDays, int evidenceRetentionDays,
            boolean legalHoldSupported, String purgeStrategy,
            Map<String, Object> configuration, boolean active) {
        public RetentionProfile {
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        }
    }

    record TenantConfiguration(
            String defaultLocale, String defaultStorageProfileCode,
            String defaultRetentionProfileCode, int schemaVersion,
            Map<String, Object> configuration) {
        public TenantConfiguration {
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        }
    }
}
