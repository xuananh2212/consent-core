package vn.com.fis.consentcore.reference.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.audit.api.AuditRecord;
import vn.com.fis.consentcore.audit.api.AuditWriter;
import vn.com.fis.consentcore.reference.api.ReferenceDataApi;
import vn.com.fis.consentcore.shared.api.CommandContext;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

@Service
class JdbcReferenceDataService implements ReferenceDataApi {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditWriter auditWriter;
    private final Clock clock;

    JdbcReferenceDataService(
            JdbcTemplate jdbc, ObjectMapper objectMapper, AuditWriter auditWriter, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID upsertPermission(PermissionDefinition definition, CommandContext context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        String code = requireText(definition.permissionCode(), "permissionCode");
        String displayName = requireText(definition.displayName(), "displayName");
        if (definition.schemaVersion() < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        UUID existing = jdbc.query("select id from permission_catalog where tenant_id = ? and permission_code = ?",
                rs -> rs.next() ? UuidUtils.fromBytes(rs.getBytes(1)) : null, context.tenantId(), code);
        UUID id = existing == null ? UUID.randomUUID() : existing;
        if (existing == null) {
            jdbc.update("""
                    insert into permission_catalog(
                        id, tenant_id, permission_code, display_name, description,
                        schema_version, attributes_schema, active)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, UuidUtils.toBytes(id), context.tenantId(), code, displayName, blankToNull(definition.description()),
                    definition.schemaVersion(), json(definition.attributesSchema()), definition.active());
        } else {
            jdbc.update("""
                    update permission_catalog
                       set display_name = ?, description = ?, schema_version = ?,
                           attributes_schema = ?, active = ?
                     where id = ? and tenant_id = ?
                    """, displayName, blankToNull(definition.description()), definition.schemaVersion(),
                    json(definition.attributesSchema()), definition.active(), UuidUtils.toBytes(id), context.tenantId());
        }

        auditWriter.append(new AuditRecord(
                UUID.randomUUID(), context.tenantId(), "PermissionCatalog", id,
                "UPSERT_PERMISSION_DEFINITION", "SUCCESS", context.actorId(), context.actorType(),
                context.sourceSystem(), context.correlationId(), context.requestId(),
                Map.of("permissionCode", code, "schemaVersion", definition.schemaVersion(),
                        "active", definition.active()), clock.instant()));
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionDefinition> listPermissions(String tenantId) {
        return jdbc.query("""
                select permission_code, display_name, description, schema_version,
                       attributes_schema, active
                  from permission_catalog where tenant_id = ? order by permission_code
                """, (rs, n) -> new PermissionDefinition(
                        rs.getString("permission_code"), rs.getString("display_name"),
                        rs.getString("description"), rs.getInt("schema_version"),
                        readMap(rs.getString("attributes_schema")), rs.getBoolean("active")),
                requireText(tenantId, "tenantId"));
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Invalid JSON schema", ex); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String value) {
        try { return objectMapper.readValue(value, Map.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid stored JSON schema", ex); }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
