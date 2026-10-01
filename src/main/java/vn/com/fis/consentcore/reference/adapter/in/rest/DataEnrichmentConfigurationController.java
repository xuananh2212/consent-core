package vn.com.fis.consentcore.reference.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.com.fis.consentcore.reference.api.DataEnrichmentConfigurationApi;
import vn.com.fis.consentcore.shared.security.RequestContextFactory;

@RestController
@RequestMapping("/api/v1/admin/reference/data-enrichment")
class DataEnrichmentConfigurationController {
    private final DataEnrichmentConfigurationApi api;
    private final RequestContextFactory contextFactory;

    DataEnrichmentConfigurationController(DataEnrichmentConfigurationApi api, RequestContextFactory contextFactory) {
        this.api = api;
        this.contextFactory = contextFactory;
    }

    @PostMapping("/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    UUID upsertRequirement(@Valid @RequestBody RequirementRequest body, HttpServletRequest request) {
        return api.upsertRequirement(new DataEnrichmentConfigurationApi.DataRequirementDefinition(
                body.consentTypeCode(), body.requirementCode(), body.resolutionPhase(), body.clientId(), body.requiredScopes(),
                body.dataType(), body.dataPurpose(), body.required(), body.schemaVersion(), body.freshnessSeconds(),
                body.configuration(), body.active()), contextFactory.create(request));
    }

    @GetMapping("/requirements")
    List<DataEnrichmentConfigurationApi.DataRequirementDefinition> requirements(
            @RequestParam(required = false) String consentTypeCode,
            @RequestParam(required = false) String resolutionPhase,
            HttpServletRequest request) {
        return api.listRequirements(contextFactory.tenantId(request), consentTypeCode, resolutionPhase);
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    UUID upsertProvider(@Valid @RequestBody ProviderRequest body, HttpServletRequest request) {
        return api.upsertProvider(new DataEnrichmentConfigurationApi.ProviderDefinition(
                body.providerCode(), body.providerType(), body.adapterKey(), body.endpointTemplate(), body.httpMethod(),
                body.credentialReference(), body.timeoutMs(), body.configuration(), body.active()),
                contextFactory.create(request));
    }

    @GetMapping("/providers")
    List<DataEnrichmentConfigurationApi.ProviderDefinition> providers(HttpServletRequest request) {
        return api.listProviders(contextFactory.tenantId(request));
    }

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    UUID upsertBinding(@Valid @RequestBody BindingRequest body, HttpServletRequest request) {
        return api.upsertBinding(new DataEnrichmentConfigurationApi.ProviderBindingDefinition(
                body.dataType(), body.consentTypeCode(), body.providerCode(), body.mappingProfileCode(),
                body.priority(), body.configuration(), body.active()), contextFactory.create(request));
    }

    @GetMapping("/bindings")
    List<DataEnrichmentConfigurationApi.ProviderBindingDefinition> bindings(HttpServletRequest request) {
        return api.listBindings(contextFactory.tenantId(request));
    }

    @PostMapping("/mapping-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    UUID upsertMapping(@Valid @RequestBody MappingRequest body, HttpServletRequest request) {
        return api.upsertMappingProfile(new DataEnrichmentConfigurationApi.MappingProfileDefinition(
                body.mappingProfileCode(), body.dataType(), body.providerCode(), body.schemaVersion(),
                body.configuration(), body.active()), contextFactory.create(request));
    }

    @GetMapping("/mapping-profiles")
    List<DataEnrichmentConfigurationApi.MappingProfileDefinition> mappings(HttpServletRequest request) {
        return api.listMappingProfiles(contextFactory.tenantId(request));
    }

    record RequirementRequest(
            @NotBlank String consentTypeCode,
            @NotBlank String requirementCode,
            @NotBlank String resolutionPhase,
            @Size(max = 200) String clientId,
            @Size(max = 50) List<@NotBlank @Size(max = 200) String> requiredScopes,
            @NotBlank String dataType,
            @NotBlank String dataPurpose,
            boolean required,
            @Min(1) int schemaVersion,
            @Positive Integer freshnessSeconds,
            @NotNull Map<String,Object> configuration,
            boolean active) { }

    record ProviderRequest(
            @NotBlank String providerCode,
            @NotBlank String providerType,
            @NotBlank String adapterKey,
            String endpointTemplate,
            String httpMethod,
            String credentialReference,
            @Min(100) @Max(120000) int timeoutMs,
            @NotNull Map<String,Object> configuration,
            boolean active) { }

    record BindingRequest(
            @NotBlank String dataType,
            String consentTypeCode,
            @NotBlank String providerCode,
            String mappingProfileCode,
            @Min(0) int priority,
            @NotNull Map<String,Object> configuration,
            boolean active) { }

    record MappingRequest(
            @NotBlank String mappingProfileCode,
            @NotBlank String dataType,
            @NotBlank String providerCode,
            @Min(1) int schemaVersion,
            @NotNull Map<String,Object> configuration,
            boolean active) { }
}
