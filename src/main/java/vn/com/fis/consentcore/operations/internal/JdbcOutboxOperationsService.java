package vn.com.fis.consentcore.operations.internal;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.audit.api.AuditRecord;
import vn.com.fis.consentcore.audit.api.AuditWriter;
import vn.com.fis.consentcore.operations.api.OutboxOperationsApi;
import vn.com.fis.consentcore.shared.api.CommandContext;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

@Service
class JdbcOutboxOperationsService implements OutboxOperationsApi {
    private final JdbcTemplate jdbc;
    private final AuditWriter auditWriter;
    private final Clock clock;

    JdbcOutboxOperationsService(JdbcTemplate jdbc, AuditWriter auditWriter, Clock clock) {
        this.jdbc = jdbc;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> summary(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        return jdbc.query("""
                select status, count(*) as total
                  from outbox_event where tenant_id = ? group by status
                """, rs -> {
                    java.util.HashMap<String, Long> result = new java.util.HashMap<>();
                    while (rs.next()) result.put(rs.getString("status"), rs.getLong("total"));
                    return Map.copyOf(result);
                }, tenantId.trim());
    }

    @Override
    @Transactional
    public boolean requeue(UUID eventId, CommandContext context) {
        if (eventId == null) throw new IllegalArgumentException("eventId must not be null");
        if (context == null) throw new IllegalArgumentException("context must not be null");
        boolean changed = jdbc.update("""
                update outbox_event
                   set status = 'PENDING', attempts = 0, available_at = current_timestamp,
                       locked_by = null, locked_at = null, last_error = null
                 where id = ? and tenant_id = ? and status = 'DEAD'
                """, UuidUtils.toBytes(eventId), context.tenantId()) == 1;
        auditWriter.append(new AuditRecord(
                UUID.randomUUID(), context.tenantId(), "OutboxEvent", eventId,
                "REQUEUE_OUTBOX_EVENT", changed ? "SUCCESS" : "FAILURE",
                context.actorId(), context.actorType(), context.sourceSystem(),
                context.correlationId(), context.requestId(), Map.of("requeued", changed), clock.instant()));
        return changed;
    }
}
