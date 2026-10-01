package vn.com.fis.consentcore.reference.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.audit.api.AuditRecord;
import vn.com.fis.consentcore.audit.api.AuditWriter;
import vn.com.fis.consentcore.reference.api.ReferenceConfigurationApi;
import vn.com.fis.consentcore.shared.api.CommandContext;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Service
class JdbcReferenceConfigurationService implements ReferenceConfigurationApi {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditWriter auditWriter;
    private final Clock clock;

    JdbcReferenceConfigurationService(
            JdbcTemplate jdbc, ObjectMapper objectMapper, AuditWriter auditWriter, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID upsertConsentType(ConsentTypeDefinition d, CommandContext c) {
        requireContext(c); requireSchemaVersion(d.schemaVersion());
        return upsertCatalog("consent_type_catalog", "consent_type_code", requireText(d.code(), "code"),
                c, "ConsentTypeCatalog", "UPSERT_CONSENT_TYPE", d.displayName(), d.description(),
                d.schemaVersion(), json(d.contentSchema()), d.active());
    }

    @Override
    @Transactional
    public UUID upsertPurpose(PurposeDefinition d, CommandContext c) {
        requireContext(c); requireSchemaVersion(d.schemaVersion());
        String code = requireText(d.code(), "code");
        UUID id = findId("purpose_definition", "purpose_code", c.tenantId(), code);
        Instant now = clock.instant();
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("""
                    insert into purpose_definition(
                      id, tenant_id, purpose_code, display_name, description, legal_basis,
                      schema_version, attributes_schema, active, created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UuidUtils.toBytes(id), c.tenantId(), code, requireText(d.displayName(), "displayName"),
                    blankToNull(d.description()), blankToNull(d.legalBasis()), d.schemaVersion(),
                    json(d.attributesSchema()), d.active(), toTimestamp(now), c.actorId(), toTimestamp(now), c.actorId());
        } else {
            jdbc.update("""
                    update purpose_definition set display_name=?, description=?, legal_basis=?,
                      schema_version=?, attributes_schema=?, active=?, updated_at=?,
                      updated_by=?, version=version+1 where id=? and tenant_id=?
                    """, requireText(d.displayName(), "displayName"), blankToNull(d.description()),
                    blankToNull(d.legalBasis()), d.schemaVersion(), json(d.attributesSchema()), d.active(),
                    toTimestamp(now), c.actorId(), UuidUtils.toBytes(id), c.tenantId());
        }
        audit("UPSERT_PURPOSE_DEFINITION", "PurposeDefinition", id, c, Map.of("code", code));
        return id;
    }

    @Override
    @Transactional
    public UUID upsertCaptureMethod(CaptureMethodDefinition d, CommandContext c) {
        requireContext(c); String code = requireText(d.code(), "code");
        UUID id = findId("capture_method_catalog", "capture_method_code", c.tenantId(), code);
        Instant now = clock.instant();
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("""
                    insert into capture_method_catalog(
                      id, tenant_id, capture_method_code, display_name, description,
                      default_evidence_type, configuration, active, created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UuidUtils.toBytes(id), c.tenantId(), code, requireText(d.displayName(), "displayName"),
                    blankToNull(d.description()), blankToNull(d.defaultEvidenceType()), json(d.configuration()),
                    d.active(), toTimestamp(now), c.actorId(), toTimestamp(now), c.actorId());
        } else {
            jdbc.update("""
                    update capture_method_catalog set display_name=?, description=?, default_evidence_type=?,
                      configuration=?, active=?, updated_at=?, updated_by=?, version=version+1
                      where id=? and tenant_id=?
                    """, requireText(d.displayName(), "displayName"), blankToNull(d.description()),
                    blankToNull(d.defaultEvidenceType()), json(d.configuration()), d.active(), toTimestamp(now),
                    c.actorId(), UuidUtils.toBytes(id), c.tenantId());
        }
        audit("UPSERT_CAPTURE_METHOD", "CaptureMethodCatalog", id, c, Map.of("code", code));
        return id;
    }

    @Override
    @Transactional
    public UUID upsertStorageProfile(StorageProfile d, CommandContext c) {
        requireContext(c); String code = requireText(d.code(), "code");
        String provider = requireText(d.providerType(), "providerType").toUpperCase();
        UUID id = findId("storage_profile", "profile_code", c.tenantId(), code);
        Instant now = clock.instant();
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("""
                    insert into storage_profile(id, tenant_id, profile_code, provider_type,
                      encryption_key_reference, configuration, active, created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UuidUtils.toBytes(id), c.tenantId(), code, provider, blankToNull(d.encryptionKeyReference()),
                    json(d.configuration()), d.active(), toTimestamp(now), c.actorId(), toTimestamp(now), c.actorId());
        } else {
            jdbc.update("""
                    update storage_profile set provider_type=?, encryption_key_reference=?,
                      configuration=?, active=?, updated_at=?, updated_by=?, version=version+1
                      where id=? and tenant_id=?
                    """, provider, blankToNull(d.encryptionKeyReference()), json(d.configuration()),
                    d.active(), toTimestamp(now), c.actorId(), UuidUtils.toBytes(id), c.tenantId());
        }
        audit("UPSERT_STORAGE_PROFILE", "StorageProfile", id, c, Map.of("code", code, "providerType", provider));
        return id;
    }

    @Override
    @Transactional
    public UUID upsertRetentionProfile(RetentionProfile d, CommandContext c) {
        requireContext(c);
        if (d.consentRetentionDays() < 1 || d.evidenceRetentionDays() < 1) {
            throw new IllegalArgumentException("retention days must be positive");
        }
        String code = requireText(d.code(), "code");
        String strategy = requireText(d.purgeStrategy(), "purgeStrategy").toUpperCase();
        UUID id = findId("retention_profile", "profile_code", c.tenantId(), code);
        Instant now = clock.instant();
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("""
                    insert into retention_profile(id, tenant_id, profile_code, consent_retention_days,
                      evidence_retention_days, legal_hold_supported, purge_strategy, configuration,
                      active, created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UuidUtils.toBytes(id), c.tenantId(), code, d.consentRetentionDays(), d.evidenceRetentionDays(),
                    d.legalHoldSupported(), strategy, json(d.configuration()), d.active(), toTimestamp(now), c.actorId(), toTimestamp(now), c.actorId());
        } else {
            jdbc.update("""
                    update retention_profile set consent_retention_days=?, evidence_retention_days=?,
                      legal_hold_supported=?, purge_strategy=?, configuration=?, active=?,
                      updated_at=?, updated_by=?, version=version+1 where id=? and tenant_id=?
                    """, d.consentRetentionDays(), d.evidenceRetentionDays(), d.legalHoldSupported(), strategy,
                    json(d.configuration()), d.active(), toTimestamp(now), c.actorId(), UuidUtils.toBytes(id), c.tenantId());
        }
        audit("UPSERT_RETENTION_PROFILE", "RetentionProfile", id, c, Map.of("code", code));
        return id;
    }

    @Override
    @Transactional
    public void upsertTenantConfiguration(TenantConfiguration d, CommandContext c) {
        requireContext(c); requireSchemaVersion(d.schemaVersion());
        Instant now = clock.instant();
        jdbc.update("""
                        merge into tenant_configuration t
                        using (select ? tenant_id, ? default_locale, ? default_storage_profile_code,
                                      ? default_retention_profile_code, ? schema_version, ? configuration,
                                      ? created_at, ? created_by, ? updated_at, ? updated_by
                                 from dual) s
                           on (t.tenant_id = s.tenant_id)
                         when matched then update set
                              t.default_locale = s.default_locale,
                              t.default_storage_profile_code = s.default_storage_profile_code,
                              t.default_retention_profile_code = s.default_retention_profile_code,
                              t.schema_version = s.schema_version,
                              t.configuration = s.configuration,
                              t.updated_at = s.updated_at,
                              t.updated_by = s.updated_by,
                              t.version = t.version + 1
                         when not matched then insert (tenant_id, default_locale, default_storage_profile_code,
                              default_retention_profile_code, schema_version, configuration, created_at, created_by,
                              updated_at, updated_by)
                              values (s.tenant_id, s.default_locale, s.default_storage_profile_code,
                              s.default_retention_profile_code, s.schema_version, s.configuration, s.created_at,
                              s.created_by, s.updated_at, s.updated_by)
                """, c.tenantId(), requireText(d.defaultLocale(), "defaultLocale"),
                blankToNull(d.defaultStorageProfileCode()), blankToNull(d.defaultRetentionProfileCode()),
                d.schemaVersion(), json(d.configuration()), toTimestamp(now), c.actorId(), toTimestamp(now), c.actorId());

        audit("UPSERT_TENANT_CONFIGURATION", "TenantConfiguration", stableTenantId(c.tenantId()), c,
                Map.of("schemaVersion", d.schemaVersion()));
    }

    @Override @Transactional(readOnly = true)
    public List<ConsentTypeDefinition> listConsentTypes(String tenantId) {
        return jdbc.query("""
                select consent_type_code, display_name, description, schema_version, content_schema, active
                from consent_type_catalog where tenant_id=? order by consent_type_code
                """, (rs,n) -> new ConsentTypeDefinition(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getInt(4), readMap(rs.getString(5)), rs.getBoolean(6)), requireText(tenantId,"tenantId"));
    }

    @Override @Transactional(readOnly = true)
    public List<PurposeDefinition> listPurposes(String tenantId) {
        return jdbc.query("""
                select purpose_code, display_name, description, legal_basis, schema_version,
                  attributes_schema, active from purpose_definition where tenant_id=? order by purpose_code
                """, (rs,n) -> new PurposeDefinition(rs.getString(1),rs.getString(2),rs.getString(3),
                rs.getString(4),rs.getInt(5),readMap(rs.getString(6)),rs.getBoolean(7)), requireText(tenantId,"tenantId"));
    }

    @Override @Transactional(readOnly = true)
    public List<CaptureMethodDefinition> listCaptureMethods(String tenantId) {
        return jdbc.query("""
                select capture_method_code, display_name, description, default_evidence_type,
                  configuration, active from capture_method_catalog where tenant_id=? order by capture_method_code
                """, (rs,n) -> new CaptureMethodDefinition(rs.getString(1),rs.getString(2),rs.getString(3),
                rs.getString(4),readMap(rs.getString(5)),rs.getBoolean(6)), requireText(tenantId,"tenantId"));
    }

    @Override @Transactional(readOnly = true)
    public List<StorageProfile> listStorageProfiles(String tenantId) {
        return jdbc.query("""
                select profile_code, provider_type, encryption_key_reference, configuration, active
                from storage_profile where tenant_id=? order by profile_code
                """, (rs,n) -> new StorageProfile(rs.getString(1),rs.getString(2),rs.getString(3),
                readMap(rs.getString(4)),rs.getBoolean(5)), requireText(tenantId,"tenantId"));
    }

    @Override @Transactional(readOnly = true)
    public List<RetentionProfile> listRetentionProfiles(String tenantId) {
        return jdbc.query("""
                select profile_code, consent_retention_days, evidence_retention_days, legal_hold_supported,
                  purge_strategy, configuration, active from retention_profile where tenant_id=? order by profile_code
                """, (rs,n) -> new RetentionProfile(rs.getString(1),rs.getInt(2),rs.getInt(3),rs.getBoolean(4),
                rs.getString(5),readMap(rs.getString(6)),rs.getBoolean(7)), requireText(tenantId,"tenantId"));
    }

    @Override @Transactional(readOnly = true)
    public TenantConfiguration getTenantConfiguration(String tenantId) {
        List<TenantConfiguration> rows = jdbc.query("""
                select default_locale, default_storage_profile_code, default_retention_profile_code,
                  schema_version, configuration from tenant_configuration where tenant_id=?
                """, (rs,n) -> new TenantConfiguration(rs.getString(1),rs.getString(2),rs.getString(3),
                rs.getInt(4),readMap(rs.getString(5))), requireText(tenantId,"tenantId"));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private UUID upsertCatalog(String table, String codeColumn, String code, CommandContext c,
            String aggregateType, String action, String displayName, String description,
            int schemaVersion, String schemaJson, boolean active) {
        UUID id = findId(table, codeColumn, c.tenantId(), code); Instant now = clock.instant();
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("insert into " + table + "(id, tenant_id, " + codeColumn + ", display_name, description, " +
                    "schema_version, content_schema, active, created_at, created_by, updated_at, updated_by) " +
                    "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", UuidUtils.toBytes(id), c.tenantId(), code,
                    requireText(displayName,"displayName"), blankToNull(description), schemaVersion, schemaJson,
                    active, toTimestamp(now), c.actorId(), toTimestamp(now), c.actorId());
        } else {
            jdbc.update("update " + table + " set display_name=?, description=?, schema_version=?, " +
                    "content_schema=?, active=?, updated_at=?, updated_by=?, version=version+1 " +
                    "where id=? and tenant_id=?", requireText(displayName,"displayName"), blankToNull(description),
                    schemaVersion, schemaJson, active, toTimestamp(now), c.actorId(), UuidUtils.toBytes(id), c.tenantId());
        }

        audit(action, aggregateType, id, c, Map.of("code", code)); return id;
    }

    private UUID findId(String table, String codeColumn, String tenantId, String code) {
        return jdbc.query("select id from " + table + " where tenant_id=? and " + codeColumn + "=?",
                rs -> rs.next() ? UuidUtils.fromBytes(rs.getBytes(1)) : null, tenantId, code);
    }

    private void audit(String action, String type, UUID id, CommandContext c, Map<String,Object> details) {
        auditWriter.append(new AuditRecord(UUID.randomUUID(), c.tenantId(), type, id, action, "SUCCESS",
                c.actorId(), c.actorType(), c.sourceSystem(), c.correlationId(), c.requestId(), details, clock.instant()));
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid JSON configuration", e); }
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> readMap(String value) {
        try { return value == null ? Map.of() : objectMapper.readValue(value, Map.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid JSON configuration in database", e); }
    }

    private static void requireContext(CommandContext c) { if (c == null) throw new IllegalArgumentException("context must not be null"); }
    private static void requireSchemaVersion(int version) { if (version < 1) throw new IllegalArgumentException("schemaVersion must be positive"); }
    private static String requireText(String v, String f) { if (v == null || v.isBlank()) throw new IllegalArgumentException(f + " must not be blank"); return v.trim(); }
    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private static UUID stableTenantId(String tenantId) { return UUID.nameUUIDFromBytes(tenantId.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
}
