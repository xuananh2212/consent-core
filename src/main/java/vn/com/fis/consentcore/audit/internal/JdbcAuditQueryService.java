package vn.com.fis.consentcore.audit.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.audit.api.AuditQueryApi;
import vn.com.fis.consentcore.shared.api.ActorType;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Service
class JdbcAuditQueryService implements AuditQueryApi {
    private static final int MAX_PAGE_SIZE = 200;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcAuditQueryService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public AuditPage search(AuditCriteria criteria) {
        if (criteria == null) throw new IllegalArgumentException("criteria must not be null");
        String tenantId = requireText(criteria.tenantId(), "tenantId");
        int page = Math.max(criteria.page(), 0);
        int size = Math.min(Math.max(criteria.size(), 1), MAX_PAGE_SIZE);

        StringBuilder where = new StringBuilder(" where tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        append(where, args, "aggregate_type = ?", criteria.aggregateType());
        if (criteria.aggregateId() != null) {
            where.append(" and aggregate_id = ?");
            args.add(UuidUtils.toBytes(criteria.aggregateId()));
        }
        append(where, args, "action = ?", criteria.action());
        append(where, args, "actor_id = ?", criteria.actorId());
        append(where, args, "correlation_id = ?", criteria.correlationId());
        if (criteria.occurredFrom() != null) {
            where.append(" and occurred_at >= ?");
            args.add(toTimestamp(criteria.occurredFrom()));
        }
        if (criteria.occurredTo() != null) {
            where.append(" and occurred_at < ?");
            args.add(toTimestamp(criteria.occurredTo()));
        }

        Long total = jdbc.queryForObject("select count(*) from audit_event" + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((long) page * size);
        pageArgs.add(size);

        List<AuditItem> items = jdbc.query("""
                select id, tenant_id, aggregate_type, aggregate_id, action, result,
                       actor_id, actor_type, source_system, correlation_id, request_id,
                       details, occurred_at
                  from audit_event
                """ + where + " order by occurred_at desc, id offset ? rows fetch next ? rows only",
                (rs, rowNum) -> new AuditItem(
                        UuidUtils.fromBytes(rs.getBytes("id")), rs.getString("tenant_id"),
                        rs.getString("aggregate_type"), UuidUtils.fromBytes(rs.getBytes("aggregate_id")),
                        rs.getString("action"), rs.getString("result"), rs.getString("actor_id"),
                        ActorType.valueOf(rs.getString("actor_type")), rs.getString("source_system"),
                        rs.getString("correlation_id"), rs.getString("request_id"),
                        readMap(rs.getString("details")), rs.getTimestamp("occurred_at").toInstant()),
                pageArgs.toArray());
        long count = total == null ? 0 : total;
        int totalPages = count == 0 ? 0 : (int) ((count + size - 1) / size);
        return new AuditPage(items, page, size, count, totalPages);
    }

    private static void append(StringBuilder where, List<Object> args, String predicate, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" and ").append(predicate);
            args.add(value.trim());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        try {
            return json == null ? Map.of() : objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid audit details JSON", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
