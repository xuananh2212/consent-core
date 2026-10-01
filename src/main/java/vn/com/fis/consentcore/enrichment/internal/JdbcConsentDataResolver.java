package vn.com.fis.consentcore.enrichment.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.com.fis.consentcore.enrichment.api.ConsentDataProvider;
import vn.com.fis.consentcore.enrichment.api.ConsentDataResolutionRequest;
import vn.com.fis.consentcore.enrichment.api.ConsentDataResolver;
import vn.com.fis.consentcore.enrichment.api.DataProviderRequest;
import vn.com.fis.consentcore.enrichment.api.DataProviderResponse;
import vn.com.fis.consentcore.enrichment.api.DataPurpose;
import vn.com.fis.consentcore.enrichment.api.ProviderConfiguration;
import vn.com.fis.consentcore.enrichment.api.ResolvedDataContext;
import vn.com.fis.consentcore.enrichment.api.ResolvedRequirementData;
import vn.com.fis.consentcore.enrichment.api.SelectionCandidate;
import vn.com.fis.consentcore.reference.api.DataEnrichmentConfigurationApi;
import vn.com.fis.consentcore.shared.error.ErrorCode;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Service
public class JdbcConsentDataResolver implements ConsentDataResolver {
    private final DataEnrichmentConfigurationApi configurationApi;
    private final List<ConsentDataProvider> providers;
    private final JdbcTemplate jdbc;
    private final ObjectMapper canonicalMapper;
    private final Clock clock;

    public JdbcConsentDataResolver(
            DataEnrichmentConfigurationApi configurationApi,
            List<ConsentDataProvider> providers,
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            Clock clock) {
        this.configurationApi = configurationApi;
        this.providers = List.copyOf(providers);
        this.jdbc = jdbc;
        this.canonicalMapper = objectMapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.clock = clock;
    }

    @Override
    public ResolvedDataContext resolve(ConsentDataResolutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.context(), "context must not be null");
        String tenantId = requireText(request.context().tenantId(), "tenantId");
        String consentType = requireText(request.consentType(), "consentType");
        String phase = Objects.requireNonNull(request.phase(), "phase must not be null").name();

        var requirements = configurationApi.getEffectiveRequirements(
                tenantId, consentType, request.clientId(), request.effectiveScopes(), phase);
        if (!request.purposes().isEmpty()) {
            requirements = requirements.stream()
                    .filter(requirement -> purposeRequested(requirement.dataPurpose(), request.purposes()))
                    .toList();
        }
        if (requirements.isEmpty()) return new ResolvedDataContext(List.of(), null);

        List<ResolvedRequirementData> resolved = new ArrayList<>();
        for (var requirement : requirements) {
            try {
                ResolvedRequirementData data = resolveOne(request, requirement);
                if (data != null) resolved.add(data);
            } catch (DataEnrichmentException ex) {
                if (requirement.required()) throw ex;
                // resolveOne logs the failed resolution once with the best available provider/context metadata.
            } catch (RuntimeException ex) {
                if (requirement.required()) {
                    throw new DataEnrichmentException(ErrorCode.CONSENT_DATA_PROVIDER_UNAVAILABLE,
                            "Required consent data could not be resolved",
                            Map.of("requirementCode", requirement.requirementCode(), "dataType", requirement.dataType()));
                }
                logResolution(request, requirement, null, "OPTIONAL_FAILURE", null, null,
                        0L, ex.getClass().getSimpleName(), clock.instant());
            }
        }

        List<Map<String,Object>> candidateHashes = resolved.stream()
                .filter(item -> item.purpose() == DataPurpose.SELECTION)
                .sorted(Comparator.comparing(ResolvedRequirementData::requirementCode))
                .map(item -> Map.<String,Object>of(
                        "requirementCode", item.requirementCode(),
                        "candidateSetHash", item.candidateSetHash()))
                .toList();
        String selectionContextHash = candidateHashes.isEmpty() ? null : hash(candidateHashes);
        return new ResolvedDataContext(resolved, selectionContextHash);
    }

    private static boolean purposeRequested(String configuredPurpose, java.util.Set<DataPurpose> requested) {
        try {
            return requested.contains(DataPurpose.valueOf(configuredPurpose));
        } catch (IllegalArgumentException ex) {
            // Let resolveOne produce the stable configuration error for an invalid configured purpose.
            return true;
        }
    }

    private ResolvedRequirementData resolveOne(
            ConsentDataResolutionRequest request,
            DataEnrichmentConfigurationApi.DataRequirementDefinition requirement) {
        String tenantId = request.context().tenantId();
        DataPurpose purpose;
        try {
            purpose = DataPurpose.valueOf(requirement.dataPurpose());
        } catch (IllegalArgumentException ex) {
            throw new DataEnrichmentException(ErrorCode.CONSENT_DATA_REQUIREMENT_UNRESOLVED,
                    "Configured data purpose is invalid", Map.of("requirementCode", requirement.requirementCode()));
        }
        var binding = configurationApi.resolveProviderBinding(tenantId, request.consentType(), requirement.dataType())
                .orElseThrow(() -> new DataEnrichmentException(ErrorCode.CONSENT_DATA_REQUIREMENT_UNRESOLVED,
                        "No active provider binding exists for required consent data",
                        Map.of("requirementCode", requirement.requirementCode(), "dataType", requirement.dataType())));
        var providerDefinition = configurationApi.getProvider(tenantId, binding.providerCode())
                .orElseThrow(() -> new DataEnrichmentException(ErrorCode.CONSENT_DATA_PROVIDER_UNAVAILABLE,
                        "Configured consent data provider is not active or does not exist",
                        Map.of("providerCode", binding.providerCode(), "dataType", requirement.dataType())));
        Map<String,Object> mappingConfiguration = binding.mappingProfileCode() == null ? Map.of()
                : configurationApi.getMappingProfile(tenantId, binding.mappingProfileCode())
                        .map(DataEnrichmentConfigurationApi.MappingProfileDefinition::configuration)
                        .orElseThrow(() -> new DataEnrichmentException(ErrorCode.CONSENT_DATA_REQUIREMENT_UNRESOLVED,
                                "Configured mapping profile is not active or does not exist",
                                Map.of("mappingProfileCode", binding.mappingProfileCode())));
        ConsentDataProvider provider = providers.stream()
                .filter(item -> item.adapterKey().equalsIgnoreCase(providerDefinition.adapterKey()))
                .findFirst()
                .orElseThrow(() -> new DataEnrichmentException(ErrorCode.CONSENT_DATA_PROVIDER_UNAVAILABLE,
                        "No ConsentDataProvider bean is registered for configured adapter key",
                        Map.of("adapterKey", providerDefinition.adapterKey(), "providerCode", providerDefinition.providerCode())));

        ProviderConfiguration providerConfiguration = new ProviderConfiguration(
                providerDefinition.providerCode(), providerDefinition.providerType(), providerDefinition.adapterKey(),
                providerDefinition.endpointTemplate(), providerDefinition.httpMethod(), providerDefinition.credentialReference(),
                providerDefinition.timeoutMs(), providerDefinition.configuration(), binding.configuration(), mappingConfiguration);
        DataProviderRequest providerRequest = new DataProviderRequest(
                tenantId, request.consentId(), request.consentType(), requirement.requirementCode(), requirement.dataType(),
                purpose, request.subjectRef(), request.clientId(), merge(request.parameters(), requirement.configuration()));

        Instant started = clock.instant();
        long nanoStarted = System.nanoTime();
        DataProviderResponse response;
        try {
            response = provider.fetch(providerRequest, providerConfiguration);
        } catch (RuntimeException ex) {
            long durationMs = Duration.ofNanos(System.nanoTime() - nanoStarted).toMillis();
            logResolution(request, requirement, providerDefinition.providerCode(),
                    requirement.required() ? "FAILED" : "OPTIONAL_FAILURE", null, null,
                    durationMs, ex.getClass().getSimpleName(), started);
            throw new DataEnrichmentException(ErrorCode.CONSENT_DATA_PROVIDER_UNAVAILABLE,
                    "Consent data provider call failed",
                    Map.of("requirementCode", requirement.requirementCode(),
                            "dataType", requirement.dataType(), "providerCode", providerDefinition.providerCode()));
        }
        if (response == null) {
            throw new DataEnrichmentException(ErrorCode.CONSENT_DATA_PROVIDER_UNAVAILABLE,
                    "Consent data provider returned no response", Map.of("providerCode", providerDefinition.providerCode()));
        }
        if (response.schemaVersion() != requirement.schemaVersion()) {
            throw new DataEnrichmentException(ErrorCode.CONSENT_DATA_REQUIREMENT_UNRESOLVED,
                    "Resolved consent data schema version does not match requirement",
                    Map.of("requirementCode", requirement.requirementCode(), "expectedSchemaVersion", requirement.schemaVersion(),
                            "actualSchemaVersion", response.schemaVersion()));
        }
        if (purpose == DataPurpose.SELECTION && requirement.required() && response.candidates().isEmpty()) {
            throw new DataEnrichmentException(ErrorCode.CONSENT_DATA_REQUIREMENT_UNRESOLVED,
                    "Required selection data returned no eligible candidates",
                    Map.of("requirementCode", requirement.requirementCode(), "dataType", requirement.dataType()));
        }

        String normalizedHash = hash(Map.of("candidates", response.candidates(), "data", response.data()));
        String candidateSetHash = purpose == DataPurpose.SELECTION ? hash(canonicalCandidates(response.candidates())) : null;
        long durationMs = Duration.ofNanos(System.nanoTime() - nanoStarted).toMillis();
        logResolution(request, requirement, providerDefinition.providerCode(), "SUCCESS", normalizedHash,
                candidateSetHash, durationMs, null, started);
        return new ResolvedRequirementData(requirement.requirementCode(), requirement.dataType(), purpose,
                requirement.required(), requirement.schemaVersion(), providerDefinition.providerCode(), response.candidates(),
                response.data(), requirement.configuration(), normalizedHash, candidateSetHash, started);
    }

    private void logResolution(
            ConsentDataResolutionRequest request,
            DataEnrichmentConfigurationApi.DataRequirementDefinition requirement,
            String providerCode,
            String resultStatus,
            String normalizedHash,
            String candidateSetHash,
            long durationMs,
            String errorCode,
            Instant resolvedAt) {
        Instant expiresAt = requirement.freshnessSeconds() == null ? null
                : resolvedAt.plusSeconds(requirement.freshnessSeconds());
        jdbc.update("""
                insert into consent_data_resolution_log(
                  id, tenant_id, consent_id, consent_type_code, requirement_code, data_type, data_purpose,
                  resolution_phase, provider_code, result_status, normalized_data_hash, candidate_set_hash,
                  duration_ms, error_code, correlation_id, resolved_at, expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UuidUtils.toBytes(UUID.randomUUID()), request.context().tenantId(),
                UuidUtils.toBytes(request.consentId()), request.consentType(),
                requirement.requirementCode(), requirement.dataType(), requirement.dataPurpose(), request.phase().name(),
                providerCode, resultStatus, normalizedHash, candidateSetHash, durationMs, errorCode,
                request.context().correlationId(), toTimestamp(resolvedAt), expiresAt == null ? null : toTimestamp(expiresAt));
    }

    private List<Map<String,Object>> canonicalCandidates(List<SelectionCandidate> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(SelectionCandidate::resourceType).thenComparing(SelectionCandidate::id))
                .map(item -> {
                    Map<String,Object> value = new LinkedHashMap<>();
                    value.put("id", item.id());
                    value.put("resourceType", item.resourceType());
                    value.put("label", item.label());
                    value.put("attributes", item.attributes());
                    return value;
                }).toList();
    }

    private String hash(Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalMapper.writeValueAsBytes(value)));
        } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
            throw new IllegalStateException("Cannot calculate consent data hash", ex);
        }
    }

    private static Map<String,Object> merge(Map<String,Object> first, Map<String,Object> second) {
        Map<String,Object> result = new LinkedHashMap<>();
        if (first != null) result.putAll(first);
        if (second != null) result.putAll(second);
        return Map.copyOf(result);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
