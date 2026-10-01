package vn.com.fis.consentcore.extension.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.audit.api.AuditRecord;
import vn.com.fis.consentcore.audit.api.AuditWriter;
import vn.com.fis.consentcore.extension.api.ExtensionAdministrationApi;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Service
class JdbcExtensionAdministrationService implements ExtensionAdministrationApi {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AuditWriter auditWriter;

    JdbcExtensionAdministrationService(
            JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock, AuditWriter auditWriter) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional
    public UUID bind(BindingCommand command) {
        if (command.context() == null) throw new IllegalArgumentException("context must not be null");
        if (command.timeoutMs() < 1 || command.maxAttempts() < 1) {
            throw new IllegalArgumentException("timeoutMs and maxAttempts must be positive");
        }
        String name = requireText(command.extensionName(), "extensionName");
        Integer registered = jdbc.queryForObject(
                "select count(*) from extension_registration where extension_name = ? and active = 1",
                Integer.class, name);
        if (registered == null || registered != 1) {
            throw new ExtensionExecutionException("Extension is not registered: " + name, null);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into extension_binding(
                    id, tenant_id, extension_name, extension_point, consent_type, client_id,
                    acquisition_channel, capture_method, evidence_type, execution_order, critical,
                    timeout_ms, max_attempts, configuration, active, created_at, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                """, UuidUtils.toBytes(id), command.context().tenantId(), name, command.extensionPoint().name(),
                blankToNull(command.consentType()), blankToNull(command.clientId()),
                command.acquisitionChannel() == null ? null : command.acquisitionChannel().name(),
                command.captureMethod() == null ? null : command.captureMethod().name(),
                blankToNull(command.evidenceType()), command.executionOrder(), command.critical(),
                command.timeoutMs(), command.maxAttempts(), json(command.configuration()),
                toTimestamp(clock.instant()), command.context().actorId());

        auditWriter.append(new AuditRecord(
                UUID.randomUUID(), command.context().tenantId(), "ExtensionBinding", id,
                "CREATE_EXTENSION_BINDING", "SUCCESS", command.context().actorId(),
                command.context().actorType(), command.context().sourceSystem(),
                command.context().correlationId(), command.context().requestId(),
                java.util.Map.of("extensionName", name,
                        "extensionPoint", command.extensionPoint().name(),
                        "critical", command.critical()), clock.instant()));

        return id;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid extension configuration", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
