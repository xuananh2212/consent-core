package vn.com.fis.consentcore.reference.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.com.fis.consentcore.reference.api.ReferenceConfigurationApi;
import vn.com.fis.consentcore.shared.security.RequestContextFactory;

@RestController
@RequestMapping("/api/v1/admin/reference")
class ReferenceConfigurationController {
    private final ReferenceConfigurationApi api;
    private final RequestContextFactory contextFactory;

    ReferenceConfigurationController(ReferenceConfigurationApi api, RequestContextFactory contextFactory) {
        this.api = api;
        this.contextFactory = contextFactory;
    }

    @PostMapping("/consent-types") @ResponseStatus(HttpStatus.CREATED)
    UUID upsertConsentType(@Valid @RequestBody ConsentTypeRequest b, HttpServletRequest r) {
        return api.upsertConsentType(new ReferenceConfigurationApi.ConsentTypeDefinition(
                b.code(), b.displayName(), b.description(), b.schemaVersion(), b.contentSchema(), b.active()),
                contextFactory.create(r));
    }
    @GetMapping("/consent-types")
    List<ReferenceConfigurationApi.ConsentTypeDefinition> consentTypes(HttpServletRequest r) {
        return api.listConsentTypes(contextFactory.tenantId(r));
    }

    @PostMapping("/purposes") @ResponseStatus(HttpStatus.CREATED)
    UUID upsertPurpose(@Valid @RequestBody PurposeRequest b, HttpServletRequest r) {
        return api.upsertPurpose(new ReferenceConfigurationApi.PurposeDefinition(
                b.code(), b.displayName(), b.description(), b.legalBasis(), b.schemaVersion(),
                b.attributesSchema(), b.active()), contextFactory.create(r));
    }
    @GetMapping("/purposes")
    List<ReferenceConfigurationApi.PurposeDefinition> purposes(HttpServletRequest r) {
        return api.listPurposes(contextFactory.tenantId(r));
    }

    @PostMapping("/capture-methods") @ResponseStatus(HttpStatus.CREATED)
    UUID upsertCaptureMethod(@Valid @RequestBody CaptureMethodRequest b, HttpServletRequest r) {
        return api.upsertCaptureMethod(new ReferenceConfigurationApi.CaptureMethodDefinition(
                b.code(), b.displayName(), b.description(), b.defaultEvidenceType(), b.configuration(), b.active()),
                contextFactory.create(r));
    }
    @GetMapping("/capture-methods")
    List<ReferenceConfigurationApi.CaptureMethodDefinition> captureMethods(HttpServletRequest r) {
        return api.listCaptureMethods(contextFactory.tenantId(r));
    }

    @PostMapping("/storage-profiles") @ResponseStatus(HttpStatus.CREATED)
    UUID upsertStorageProfile(@Valid @RequestBody StorageProfileRequest b, HttpServletRequest r) {
        return api.upsertStorageProfile(new ReferenceConfigurationApi.StorageProfile(
                b.code(), b.providerType(), b.encryptionKeyReference(), b.configuration(), b.active()),
                contextFactory.create(r));
    }
    @GetMapping("/storage-profiles")
    List<ReferenceConfigurationApi.StorageProfile> storageProfiles(HttpServletRequest r) {
        return api.listStorageProfiles(contextFactory.tenantId(r));
    }

    @PostMapping("/retention-profiles") @ResponseStatus(HttpStatus.CREATED)
    UUID upsertRetentionProfile(@Valid @RequestBody RetentionProfileRequest b, HttpServletRequest r) {
        return api.upsertRetentionProfile(new ReferenceConfigurationApi.RetentionProfile(
                b.code(), b.consentRetentionDays(), b.evidenceRetentionDays(), b.legalHoldSupported(),
                b.purgeStrategy(), b.configuration(), b.active()), contextFactory.create(r));
    }
    @GetMapping("/retention-profiles")
    List<ReferenceConfigurationApi.RetentionProfile> retentionProfiles(HttpServletRequest r) {
        return api.listRetentionProfiles(contextFactory.tenantId(r));
    }

    @PutMapping("/tenant-configuration") @ResponseStatus(HttpStatus.NO_CONTENT)
    void upsertTenantConfiguration(@Valid @RequestBody TenantConfigurationRequest b, HttpServletRequest r) {
        api.upsertTenantConfiguration(new ReferenceConfigurationApi.TenantConfiguration(
                b.defaultLocale(), b.defaultStorageProfileCode(), b.defaultRetentionProfileCode(),
                b.schemaVersion(), b.configuration()), contextFactory.create(r));
    }
    @GetMapping("/tenant-configuration")
    ReferenceConfigurationApi.TenantConfiguration tenantConfiguration(HttpServletRequest r) {
        return api.getTenantConfiguration(contextFactory.tenantId(r));
    }

    record ConsentTypeRequest(@NotBlank String code, @NotBlank String displayName, String description,
                              @Min(1) int schemaVersion, @NotNull Map<String,Object> contentSchema, boolean active) { }
    record PurposeRequest(@NotBlank String code, @NotBlank String displayName, String description,
                          String legalBasis, @Min(1) int schemaVersion,
                          @NotNull Map<String,Object> attributesSchema, boolean active) { }
    record CaptureMethodRequest(@NotBlank String code, @NotBlank String displayName, String description,
                                String defaultEvidenceType, @NotNull Map<String,Object> configuration,
                                boolean active) { }
    record StorageProfileRequest(@NotBlank String code, @NotBlank String providerType,
                                 String encryptionKeyReference, @NotNull Map<String,Object> configuration,
                                 boolean active) { }
    record RetentionProfileRequest(@NotBlank String code, @Min(1) int consentRetentionDays,
                                   @Min(1) int evidenceRetentionDays, boolean legalHoldSupported,
                                   @NotBlank String purgeStrategy, @NotNull Map<String,Object> configuration,
                                   boolean active) { }
    record TenantConfigurationRequest(@NotBlank String defaultLocale, String defaultStorageProfileCode,
                                      String defaultRetentionProfileCode, @Min(1) int schemaVersion,
                                      @NotNull Map<String,Object> configuration) { }
}
