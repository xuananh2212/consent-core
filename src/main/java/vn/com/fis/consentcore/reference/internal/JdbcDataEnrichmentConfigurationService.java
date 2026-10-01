package vn.com.fis.consentcore.reference.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.audit.api.AuditRecord;
import vn.com.fis.consentcore.audit.api.AuditWriter;
import vn.com.fis.consentcore.reference.api.DataEnrichmentConfigurationApi;
import vn.com.fis.consentcore.shared.api.CommandContext;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Service
public class JdbcDataEnrichmentConfigurationService implements DataEnrichmentConfigurationApi {
    private static final Set<String> PHASES = Set.of("REGISTRATION", "PREPARE_AUTHORIZATION", "AUTHORIZATION_VALIDATION");
    private static final Set<String> PURPOSES = Set.of("SELECTION", "CONTEXT", "POLICY");
    private static final Set<String> PROVIDER_TYPES = Set.of("MOCK", "REST", "SOAP", "DATABASE", "MESSAGE", "COMPOSITE", "CUSTOM");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public JdbcDataEnrichmentConfigurationService(
            JdbcTemplate jdbc, ObjectMapper mapper, AuditWriter auditWriter, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID upsertRequirement(DataRequirementDefinition d, CommandContext c) {
        requireContext(c);
        String consentType = requireText(d.consentTypeCode(), "consentTypeCode");
        String code = requireText(d.requirementCode(), "requirementCode");
        String phase = upperAllowed(d.resolutionPhase(), "resolutionPhase", PHASES);
        String purpose = upperAllowed(d.dataPurpose(), "dataPurpose", PURPOSES);
        String dataType = requireText(d.dataType(), "dataType");
        String clientId = blankToNull(d.clientId());
        List<String> requiredScopes = normalizeScopes(d.requiredScopes());
        if (d.schemaVersion() < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        if (d.freshnessSeconds() != null && d.freshnessSeconds() <= 0) {
            throw new IllegalArgumentException("freshnessSeconds must be positive when provided");
        }
        UUID id = findRequirementId(c.tenantId(), consentType, phase, code, clientId, requiredScopes);
        Instant now = clock.instant();
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("""
                    insert into consent_data_requirement(
                      id, tenant_id, consent_type_code, requirement_code, resolution_phase, client_id, required_scopes,
                      data_type, data_purpose, required, schema_version, freshness_seconds, configuration, active,
                      created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UuidUtils.toBytes(id), c.tenantId(), consentType, code, phase, clientId, json(requiredScopes), dataType, purpose,
                    d.required(), d.schemaVersion(), d.freshnessSeconds(), json(d.configuration()), d.active(),
                    ts(now), c.actorId(), ts(now), c.actorId());
        } else {
            jdbc.update("""
                    update consent_data_requirement set data_type=?, data_purpose=?, required=?, schema_version=?,
                      freshness_seconds=?, configuration=?, active=?, updated_at=?, updated_by=?,
                      version=version+1
                    where id=? and tenant_id=?
                    """, dataType, purpose, d.required(), d.schemaVersion(), d.freshnessSeconds(),
                    json(d.configuration()), d.active(), ts(now), c.actorId(), UuidUtils.toBytes(id), c.tenantId());
        }
        Map<String,Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("consentType", consentType);
        auditDetails.put("requirementCode", code);
        auditDetails.put("phase", phase);
        auditDetails.put("dataType", dataType);
        if (clientId != null) auditDetails.put("clientId", clientId);
        auditDetails.put("requiredScopes", requiredScopes);
        audit("UPSERT_DATA_REQUIREMENT", "ConsentDataRequirement", id, c, auditDetails);
        return id;
    }

    @Override
    @Transactional
    public UUID upsertProvider(ProviderDefinition d, CommandContext c) {
        requireContext(c);
        String code = requireText(d.providerCode(), "providerCode");
        String type = upperAllowed(d.providerType(), "providerType", PROVIDER_TYPES);
        String adapterKey = requireText(d.adapterKey(), "adapterKey");
        String method = blankToNull(d.httpMethod());
        if (method != null) {
            method = method.toUpperCase();
            if (!Set.of("GET", "POST").contains(method)) throw new IllegalArgumentException("httpMethod must be GET or POST");
        }
        if (d.timeoutMs() < 100 || d.timeoutMs() > 120000) throw new IllegalArgumentException("timeoutMs must be between 100 and 120000");
        UUID id = findByCode("data_provider_definition", "provider_code", c.tenantId(), code);
        Instant now = clock.instant();
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("""
                    insert into data_provider_definition(
                      id, tenant_id, provider_code, provider_type, adapter_key, endpoint_template, http_method,
                      credential_reference, timeout_ms, configuration, active, created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UuidUtils.toBytes(id), c.tenantId(), code, type, adapterKey, blankToNull(d.endpointTemplate()), method,
                    blankToNull(d.credentialReference()), d.timeoutMs(), json(d.configuration()), d.active(),
                    ts(now), c.actorId(), ts(now), c.actorId());
        } else {
            jdbc.update("""
                    update data_provider_definition set provider_type=?, adapter_key=?, endpoint_template=?, http_method=?,
                      credential_reference=?, timeout_ms=?, configuration=?, active=?, updated_at=?, updated_by=?,
                      version=version+1 where id=? and tenant_id=?
                    """, type, adapterKey, blankToNull(d.endpointTemplate()), method, blankToNull(d.credentialReference()),
                    d.timeoutMs(), json(d.configuration()), d.active(), ts(now), c.actorId(), UuidUtils.toBytes(id), c.tenantId());
        }
        audit("UPSERT_DATA_PROVIDER", "DataProviderDefinition", id, c, Map.of("providerCode", code, "providerType", type));
        return id;
    }

    @Override
    @Transactional
    public UUID upsertBinding(ProviderBindingDefinition d, CommandContext c) {
        requireContext(c);
        String dataType = requireText(d.dataType(), "dataType");
        String provider = requireText(d.providerCode(), "providerCode");
        String consentType = blankToNull(d.consentTypeCode());
        String mapping = blankToNull(d.mappingProfileCode());
        if (d.priority() < 0) throw new IllegalArgumentException("priority must not be negative");
        UUID id = findBindingId(c.tenantId(), dataType, consentType, provider);
        Instant now = clock.instant();
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("""
                    insert into data_provider_binding(
                      id, tenant_id, data_type, consent_type_code, provider_code, mapping_profile_code, priority,
                      configuration, active, created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UuidUtils.toBytes(id), c.tenantId(), dataType, consentType, provider, mapping, d.priority(),
                    json(d.configuration()), d.active(), ts(now), c.actorId(), ts(now), c.actorId());
        } else {
            jdbc.update("""
                    update data_provider_binding set mapping_profile_code=?, priority=?, configuration=?,
                      active=?, updated_at=?, updated_by=?, version=version+1 where id=? and tenant_id=?
                    """, mapping, d.priority(), json(d.configuration()), d.active(), ts(now), c.actorId(), UuidUtils.toBytes(id), c.tenantId());
        }
        audit("UPSERT_DATA_PROVIDER_BINDING", "DataProviderBinding", id, c,
                Map.of("dataType", dataType, "providerCode", provider));
        return id;
    }

    @Override
    @Transactional
    public UUID upsertMappingProfile(MappingProfileDefinition d, CommandContext c) {
        requireContext(c);
        String code = requireText(d.mappingProfileCode(), "mappingProfileCode");
        String dataType = requireText(d.dataType(), "dataType");
        String provider = requireText(d.providerCode(), "providerCode");
        if (d.schemaVersion() < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        UUID id = findByCode("data_mapping_profile", "mapping_profile_code", c.tenantId(), code);
        Instant now = clock.instant();
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("""
                    insert into data_mapping_profile(
                      id, tenant_id, mapping_profile_code, data_type, provider_code, schema_version,
                      configuration, active, created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UuidUtils.toBytes(id), c.tenantId(), code, dataType, provider, d.schemaVersion(), json(d.configuration()),
                    d.active(), ts(now), c.actorId(), ts(now), c.actorId());
        } else {
            jdbc.update("""
                    update data_mapping_profile set data_type=?, provider_code=?, schema_version=?,
                      configuration=?, active=?, updated_at=?, updated_by=?, version=version+1
                    where id=? and tenant_id=?
                    """, dataType, provider, d.schemaVersion(), json(d.configuration()), d.active(),
                    ts(now), c.actorId(), UuidUtils.toBytes(id), c.tenantId());
        }
        audit("UPSERT_DATA_MAPPING_PROFILE", "DataMappingProfile", id, c,
                Map.of("mappingProfileCode", code, "dataType", dataType, "providerCode", provider));
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataRequirementDefinition> listRequirements(String tenantId, String consentTypeCode, String resolutionPhase) {
        tenantId = requireText(tenantId, "tenantId");
        StringBuilder sql = new StringBuilder("""
                select consent_type_code, requirement_code, resolution_phase, client_id, required_scopes,
                       data_type, data_purpose, required, schema_version, freshness_seconds, configuration, active
                  from consent_data_requirement where tenant_id=?
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(tenantId);
        if (consentTypeCode != null && !consentTypeCode.isBlank()) { sql.append(" and consent_type_code=?"); args.add(consentTypeCode.trim()); }
        if (resolutionPhase != null && !resolutionPhase.isBlank()) { sql.append(" and resolution_phase=?"); args.add(resolutionPhase.trim().toUpperCase()); }
        sql.append(" order by consent_type_code, resolution_phase, requirement_code, client_id nulls first, dbms_lob.substr(required_scopes, 4000, 1)");
        return jdbc.query(sql.toString(), (rs,n) -> new DataRequirementDefinition(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), readStringList(rs.getString(5)),
                rs.getString(6), rs.getString(7), rs.getBoolean(8), rs.getInt(9), rs.getObject(10) == null ? null : rs.getInt(10),
                readMap(rs.getString(11)), rs.getBoolean(12)), args.toArray());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderDefinition> listProviders(String tenantId) {
        return jdbc.query("""
                select provider_code, provider_type, adapter_key, endpoint_template, http_method, credential_reference,
                       timeout_ms, configuration, active
                  from data_provider_definition where tenant_id=? order by provider_code
                """, (rs,n) -> new ProviderDefinition(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                rs.getString(5),rs.getString(6),rs.getInt(7),readMap(rs.getString(8)),rs.getBoolean(9)),
                requireText(tenantId,"tenantId"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderBindingDefinition> listBindings(String tenantId) {
        return jdbc.query("""
                select data_type, consent_type_code, provider_code, mapping_profile_code, priority, configuration, active
                  from data_provider_binding where tenant_id=? order by data_type, consent_type_code nulls last, priority, provider_code
                """, (rs,n) -> new ProviderBindingDefinition(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                rs.getInt(5),readMap(rs.getString(6)),rs.getBoolean(7)), requireText(tenantId,"tenantId"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MappingProfileDefinition> listMappingProfiles(String tenantId) {
        return jdbc.query("""
                select mapping_profile_code, data_type, provider_code, schema_version, configuration, active
                  from data_mapping_profile where tenant_id=? order by mapping_profile_code
                """, (rs,n) -> new MappingProfileDefinition(rs.getString(1),rs.getString(2),rs.getString(3),
                rs.getInt(4),readMap(rs.getString(5)),rs.getBoolean(6)), requireText(tenantId,"tenantId"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataRequirementDefinition> getEffectiveRequirements(
            String tenantId,
            String consentTypeCode,
            String clientId,
            List<String> effectiveScopes,
            String resolutionPhase) {
        tenantId = requireText(tenantId, "tenantId");
        consentTypeCode = requireText(consentTypeCode, "consentTypeCode");
        clientId = blankToNull(clientId);
        List<String> scopes = normalizeScopes(effectiveScopes);
        String phase = upperAllowed(resolutionPhase, "resolutionPhase", PHASES);
        final String requestedClientId = clientId;

        List<DataRequirementDefinition> candidates = jdbc.query("""
                select consent_type_code, requirement_code, resolution_phase, client_id, required_scopes,
                       data_type, data_purpose, required, schema_version, freshness_seconds, configuration, active
                  from consent_data_requirement
                 where tenant_id=? and consent_type_code=? and resolution_phase=? and active=1
                """, (rs,n) -> new DataRequirementDefinition(
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), readStringList(rs.getString(5)),
                        rs.getString(6), rs.getString(7), rs.getBoolean(8), rs.getInt(9),
                        rs.getObject(10) == null ? null : rs.getInt(10),
                        readMap(rs.getString(11)), rs.getBoolean(12)),
                tenantId, consentTypeCode, phase);

        java.util.Set<String> scopeSet = new java.util.HashSet<>(scopes);

        List<DataRequirementDefinition> matches = candidates.stream()
                .filter(item -> item.clientId() == null || item.clientId().equals(requestedClientId))
                .filter(item -> scopeSet.containsAll(item.requiredScopes()))
                .sorted(java.util.Comparator.comparing(DataRequirementDefinition::requirementCode)
                        .thenComparing(item -> item.clientId() == null ? 0 : 1)
                        .thenComparing(item -> -item.requiredScopes().size()))
                .toList();

        Map<String,List<DataRequirementDefinition>> byCode = matches.stream().collect(
                java.util.stream.Collectors.groupingBy(DataRequirementDefinition::requirementCode,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));

        List<String> ambiguous = byCode.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .toList();

        if (!ambiguous.isEmpty()) {
            throw new IllegalStateException("Ambiguous effective consent data requirements for requirementCode(s): "
                    + String.join(", ", ambiguous));
        }

        return matches;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderBindingDefinition> resolveProviderBinding(String tenantId, String consentTypeCode, String dataType) {
        List<ProviderBindingDefinition> rows = jdbc.query("""
                select data_type, consent_type_code, provider_code, mapping_profile_code, priority, configuration, active
                  from data_provider_binding
                 where tenant_id=? and data_type=? and active=1
                   and (consent_type_code=? or consent_type_code is null)
                 order by case when consent_type_code=? then 0 else 1 end, priority, provider_code
                 fetch first 1 rows only
                """, (rs,n) -> new ProviderBindingDefinition(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                rs.getInt(5),readMap(rs.getString(6)),rs.getBoolean(7)), requireText(tenantId,"tenantId"),
                requireText(dataType,"dataType"), requireText(consentTypeCode,"consentTypeCode"), consentTypeCode.trim());
        return rows.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderDefinition> getProvider(String tenantId, String providerCode) {
        List<ProviderDefinition> rows = jdbc.query("""
                select provider_code, provider_type, adapter_key, endpoint_template, http_method, credential_reference,
                       timeout_ms, configuration, active
                  from data_provider_definition where tenant_id=? and provider_code=? and active=1
                """, (rs,n) -> new ProviderDefinition(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                rs.getString(5),rs.getString(6),rs.getInt(7),readMap(rs.getString(8)),rs.getBoolean(9)),
                requireText(tenantId,"tenantId"), requireText(providerCode,"providerCode"));
        return rows.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MappingProfileDefinition> getMappingProfile(String tenantId, String mappingProfileCode) {
        if (mappingProfileCode == null || mappingProfileCode.isBlank()) return Optional.empty();
        List<MappingProfileDefinition> rows = jdbc.query("""
                select mapping_profile_code, data_type, provider_code, schema_version, configuration, active
                  from data_mapping_profile where tenant_id=? and mapping_profile_code=? and active=1
                """, (rs,n) -> new MappingProfileDefinition(rs.getString(1),rs.getString(2),rs.getString(3),
                rs.getInt(4),readMap(rs.getString(5)),rs.getBoolean(6)), requireText(tenantId,"tenantId"), mappingProfileCode.trim());
        return rows.stream().findFirst();
    }

    private UUID findRequirementId(
            String tenantId, String consentType, String phase, String code, String clientId, List<String> requiredScopes) {
        return jdbc.query("""
                select id from consent_data_requirement where tenant_id=? and consent_type_code=?
                  and resolution_phase=? and requirement_code=?
                  and decode(client_id, ?, 1, 0) = 1
                  and dbms_lob.compare(required_scopes, to_clob(?)) = 0
                """, rs -> rs.next() ? UuidUtils.fromBytes(rs.getBytes(1)) : null,
                tenantId, consentType, phase, code,
                new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.VARCHAR, clientId),
                json(requiredScopes));
    }

    private UUID findBindingId(String tenantId, String dataType, String consentType, String provider) {
        return jdbc.query("""
            select id from data_provider_binding where tenant_id=? and data_type=?
              and decode(consent_type_code, ?, 1, 0) = 1
              and provider_code=?
            """, rs -> rs.next() ? UuidUtils.fromBytes(rs.getBytes(1)) : null,
                tenantId, dataType,
                new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.VARCHAR, consentType),
                provider);
    }

    private UUID findByCode(String table, String column, String tenantId, String code) {
        return jdbc.query("select id from " + table + " where tenant_id=? and " + column + "=?",
                rs -> rs.next() ? UuidUtils.fromBytes(rs.getBytes(1)) : null, tenantId, code);
    }

    private void audit(String action, String type, UUID id, CommandContext c, Map<String,Object> details) {
        auditWriter.append(new AuditRecord(UUID.randomUUID(), c.tenantId(), type, id, action, "SUCCESS",
                c.actorId(), c.actorType(), c.sourceSystem(), c.correlationId(), c.requestId(), details, clock.instant()));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid JSON configuration", e); }
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> readMap(String value) {
        try { return value == null ? Map.of() : mapper.readValue(value, Map.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid JSON configuration in database", e); }
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(String value) {
        try {
            if (value == null) return List.of();
            List<Object> raw = mapper.readValue(value, List.class);
            return normalizeScopes(raw.stream().filter(java.util.Objects::nonNull).map(Object::toString).toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid scope JSON configuration in database", e);
        }
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

    private static Timestamp ts(Instant value) { return toTimestamp(value); }
    private static void requireContext(CommandContext c) { if (c == null) throw new IllegalArgumentException("context must not be null"); }
    private static String requireText(String v, String f) { if (v == null || v.isBlank()) throw new IllegalArgumentException(f + " must not be blank"); return v.trim(); }
    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private static String upperAllowed(String value, String field, Set<String> allowed) {
        String normalized = requireText(value, field).toUpperCase();
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(field + " has unsupported value: " + value);
        return normalized;
    }
}
