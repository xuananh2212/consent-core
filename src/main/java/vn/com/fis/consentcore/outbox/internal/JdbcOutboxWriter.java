package vn.com.fis.consentcore.outbox.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.com.fis.consentcore.outbox.api.OutboxWriter;
import vn.com.fis.consentcore.shared.api.CommandContext;
import vn.com.fis.consentcore.shared.domain.DomainEvent;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Component
class JdbcOutboxWriter implements OutboxWriter {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JdbcOutboxWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void append(DomainEvent event, CommandContext context) {
        Instant now = clock.instant();
        jdbcTemplate.update("""
                insert into outbox_event (
                    id, tenant_id, aggregate_type, aggregate_id, event_type, event_version,
                    payload, correlation_id, occurred_at, status, attempts, available_at, created_at
                ) values (?, ?, 'Consent', ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)
                """,
                UuidUtils.toBytes(event.eventId()),
                event.tenantId(),
                UuidUtils.toBytes(event.aggregateId()),
                event.eventType(),
                event.eventVersion(),
                toJson(event),
                context.correlationId(),
                toTimestamp(event.occurredAt()),
                toTimestamp(now),
                toTimestamp(now)
        );
    }

    private String toJson(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize domain event " + event.eventType(), e);
        }
    }
}
