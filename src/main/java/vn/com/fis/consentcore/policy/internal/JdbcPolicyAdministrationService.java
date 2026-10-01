package vn.com.fis.consentcore.policy.internal;

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
import vn.com.fis.consentcore.policy.api.PolicyAdministrationApi;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Service
class JdbcPolicyAdministrationService implements PolicyAdministrationApi {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AuditWriter auditWriter;

    JdbcPolicyAdministrationService(
            JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock, AuditWriter auditWriter) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional
    public UUID upsertConsentTypePolicy(ConsentTypePolicyCommand command) {
        requireContext(command.context());

        if (command.maxValidityDays() < 1) throw new IllegalArgumentException("maxValidityDays must be positive");

        String consentType = requireText(command.consentType(), "consentType");

        UUID id = jdbc.query("select id from consent_type_policy where tenant_id = ? and consent_type = ?",
                rs -> rs.next() ? UuidUtils.fromBytes(rs.getBytes(1)) : null,
                command.context().tenantId(), consentType);

        Instant now = clock.instant();

        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("""
                    insert into consent_type_policy(
                        id, tenant_id, consent_type, evidence_policy, max_validity_days,
                        require_subject, allow_trusted_auto_authorization, allowed_channels,
                        active, created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)
                    """, UuidUtils.toBytes(id), command.context().tenantId(), consentType, command.evidencePolicy().name(),
                    command.maxValidityDays(), command.requireSubject(), command.allowTrustedAutoAuthorization(),
                    json(command.allowedChannels().stream().map(Enum::name).toList()),
                    toTimestamp(now), command.context().actorId(), toTimestamp(now), command.context().actorId());
        } else {
            jdbc.update("""
                    update consent_type_policy
                       set evidence_policy = ?, max_validity_days = ?, require_subject = ?,
                           allow_trusted_auto_authorization = ?, allowed_channels = ?,
                           active = 1, updated_at = ?, updated_by = ?, version = version + 1
                     where id = ? and tenant_id = ?
                    """, command.evidencePolicy().name(), command.maxValidityDays(), command.requireSubject(),
                    command.allowTrustedAutoAuthorization(),
                    json(command.allowedChannels().stream().map(Enum::name).toList()),
                    toTimestamp(now), command.context().actorId(), UuidUtils.toBytes(id), command.context().tenantId());
        }

        appendAudit("UPSERT_CONSENT_TYPE_POLICY", "ConsentTypePolicy", id, command.context(),
                java.util.Map.of("consentType", consentType,
                        "evidencePolicy", command.evidencePolicy().name(),
                        "maxValidityDays", command.maxValidityDays()));
        return id;
    }

    @Override
    @Transactional
    public UUID upsertTrustedSource(TrustedSourceCommand command) {
        requireContext(command.context());
        if (command.validFrom() != null && command.validUntil() != null
                && !command.validUntil().isAfter(command.validFrom())) {
            throw new IllegalArgumentException("validUntil must be after validFrom");
        }
        String sourceSystem = requireText(command.sourceSystem(), "sourceSystem");
        // PostgreSQL UNIQUE treats nulls as distinct. Normalize a source-wide rule to an empty client id.
        String clientId = command.clientId() == null ? "" : command.clientId().trim();
        UUID existing = jdbc.query("""
                select id from trusted_consent_source
                 where tenant_id = ? and source_system = ? and coalesce(client_id, '') = ?
                """, rs -> rs.next() ? UuidUtils.fromBytes(rs.getBytes(1)) : null,
                command.context().tenantId(), sourceSystem, clientId);
        UUID id = existing == null ? UUID.randomUUID() : existing;
        Instant now = clock.instant();
        if (existing == null) {
            jdbc.update("""
                    insert into trusted_consent_source(
                        id, tenant_id, source_system, client_id, allowed_consent_types,
                        active, valid_from, valid_until, created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, nullif(?, ''), ?, 1, ?, ?, ?, ?, ?, ?)
                    """, UuidUtils.toBytes(id), command.context().tenantId(), sourceSystem, clientId,
                    json(command.allowedConsentTypes()), toTimestamp(command.validFrom()), toTimestamp(command.validUntil()),
                    toTimestamp(now), command.context().actorId(), toTimestamp(now), command.context().actorId());
        } else {
            jdbc.update("""
                    update trusted_consent_source
                       set allowed_consent_types = ?, active = 1,
                           valid_from = ?, valid_until = ?, updated_at = ?, updated_by = ?
                     where id = ? and tenant_id = ?
                    """, json(command.allowedConsentTypes()), toTimestamp(command.validFrom()), toTimestamp(command.validUntil()),
                    toTimestamp(now), command.context().actorId(), UuidUtils.toBytes(id), command.context().tenantId());
        }
        appendAudit("UPSERT_TRUSTED_SOURCE", "TrustedConsentSource", id, command.context(),
                java.util.Map.of("sourceSystem", sourceSystem,
                        "clientId", clientId,
                        "allowedConsentTypes", command.allowedConsentTypes()));
        return id;
    }


    @Override
    @Transactional
    public UUID publishPolicyDefinition(PolicyDefinitionCommand command) {
        requireContext(command.context());
        if (command.policyVersion() < 1) {
            throw new IllegalArgumentException("policyVersion must be positive");
        }
        if (command.effectiveFrom() != null && command.effectiveUntil() != null
                && !command.effectiveUntil().isAfter(command.effectiveFrom())) {
            throw new IllegalArgumentException("effectiveUntil must be after effectiveFrom");
        }
        String code = requireText(command.policyCode(), "policyCode");
        String type = requireText(command.policyType(), "policyType").toUpperCase();
        String status = requireText(command.status(), "status").toUpperCase();
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update("""
                insert into policy_definition(
                    id, tenant_id, policy_code, policy_version, policy_type, status,
                    definition, effective_from, effective_until, created_at, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UuidUtils.toBytes(id), command.context().tenantId(), code, command.policyVersion(), type, status,
                json(command.definition()), toTimestamp(command.effectiveFrom()), toTimestamp(command.effectiveUntil()), toTimestamp(now),
                command.context().actorId());
        appendAudit("PUBLISH_POLICY_DEFINITION", "PolicyDefinition", id, command.context(),
                Map.of("policyCode", code, "policyVersion", command.policyVersion(),
                        "policyType", type, "status", status));
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyDefinition> listPolicyDefinitions(String tenantId, String policyType) {
        String tenant = requireText(tenantId, "tenantId");
        String type = policyType == null || policyType.isBlank() ? null : policyType.trim().toUpperCase();
        String sql = type == null
                ? """
                  select id, policy_code, policy_version, policy_type, status, definition,
                         effective_from, effective_until, created_at, created_by
                    from policy_definition where tenant_id = ?
                   order by policy_code, policy_version desc
                  """
                : """
                  select id, policy_code, policy_version, policy_type, status, definition,
                         effective_from, effective_until, created_at, created_by
                    from policy_definition where tenant_id = ? and policy_type = ?
                   order by policy_code, policy_version desc
                  """;
        Object[] args = type == null ? new Object[]{tenant} : new Object[]{tenant, type};
        return jdbc.query(sql, (rs, rowNum) -> new PolicyDefinition(
                UuidUtils.fromBytes(rs.getBytes("id")), rs.getString("policy_code"),
                rs.getInt("policy_version"), rs.getString("policy_type"), rs.getString("status"),
                readMap(rs.getString("definition")),
                rs.getTimestamp("effective_from") == null ? null : rs.getTimestamp("effective_from").toInstant(),
                rs.getTimestamp("effective_until") == null ? null : rs.getTimestamp("effective_until").toInstant(),
                rs.getTimestamp("created_at").toInstant(), rs.getString("created_by")), args);
    }

    private void appendAudit(
            String action, String aggregateType, UUID aggregateId,
            vn.com.fis.consentcore.shared.api.CommandContext context,
            java.util.Map<String, Object> details) {
        auditWriter.append(new AuditRecord(
                UUID.randomUUID(), context.tenantId(), aggregateType, aggregateId,
                action, "SUCCESS", context.actorId(), context.actorType(), context.sourceSystem(),
                context.correlationId(), context.requestId(), details, clock.instant()));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid JSON configuration", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String value) {
        try {
            return value == null ? Map.of() : objectMapper.readValue(value, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid policy definition JSON", exception);
        }
    }

    private static void requireContext(vn.com.fis.consentcore.shared.api.CommandContext context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
