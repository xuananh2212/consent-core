package vn.com.fis.consentcore.audit.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.com.fis.consentcore.audit.api.AuditRecord;
import vn.com.fis.consentcore.audit.api.AuditWriter;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Component
class JdbcAuditWriter implements AuditWriter {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    JdbcAuditWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(AuditRecord record) {
        jdbcTemplate.update("""
                insert into audit_event (
                    id, tenant_id, aggregate_type, aggregate_id, action, result,
                    actor_id, actor_type, source_system, correlation_id, request_id,
                    details, occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UuidUtils.toBytes(record.id()),
                record.tenantId(),
                record.aggregateType(),
                UuidUtils.toBytes(record.aggregateId()),
                record.action(),
                record.result(),
                record.actorId(),
                record.actorType().name(),
                record.sourceSystem(),
                record.correlationId(),
                record.requestId(),
                toJson(record.details()),
                toTimestamp(record.occurredAt())
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize audit details", e);
        }
    }
}
