package vn.com.fis.consentcore.registry.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.com.fis.consentcore.registry.application.internal.port.DecisionCapturePort;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Repository
class JdbcDecisionCaptureAdapter implements DecisionCapturePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcDecisionCaptureAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public UUID append(DecisionCaptureRecord record) {
        jdbc.update("""
                insert into consent_decision(
                    id, tenant_id, consent_id, revision_id, outcome,
                    decision_maker_id, captured_by, capture_method, capture_location,
                    authorization_reference, authentication_context, decided_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UuidUtils.toBytes(record.id()), record.tenantId(), UuidUtils.toBytes(record.consentId()), UuidUtils.toBytes(record.revisionId()),
                record.outcome(), record.decisionMakerId(), record.capturedBy(),
                record.captureMethod().name(), record.captureLocation(), record.authorizationReference(),
                json(record.authenticationContext()), toTimestamp(record.decidedAt()), toTimestamp(record.createdAt()));

        return record.id();
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Decision authentication context is not valid JSON", exception);
        }
    }
}
