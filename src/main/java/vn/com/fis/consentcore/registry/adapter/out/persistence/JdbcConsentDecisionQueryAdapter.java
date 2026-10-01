package vn.com.fis.consentcore.registry.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.registry.api.result.ConsentDecisionResult;
import vn.com.fis.consentcore.registry.api.usecase.GetConsentDecisionsUseCase;
import vn.com.fis.consentcore.registry.application.internal.exception.ConsentNotFoundException;
import vn.com.fis.consentcore.registry.domain.model.CaptureMethod;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

@Component
class JdbcConsentDecisionQueryAdapter implements GetConsentDecisionsUseCase {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcConsentDecisionQueryAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsentDecisionResult> getDecisions(String tenantId, UUID consentId) {
        Integer consentCount = jdbc.queryForObject(
                "select count(*) from consent where tenant_id = ? and id = ?",
                Integer.class, tenantId, UuidUtils.toBytes(consentId));

        if (consentCount == null || consentCount != 1) {
            throw new ConsentNotFoundException(tenantId, consentId);
        }

        return jdbc.query("""
                select id, consent_id, revision_id, outcome, decision_maker_id as subject_id, captured_by,
                       capture_method, capture_location, authorization_reference,
                       authentication_context as decision_data, decided_at, created_at
                  from consent_decision
                 where tenant_id = ? and consent_id = ?
                 order by decided_at, id
                """, (rs, n) -> new ConsentDecisionResult(
                        UuidUtils.fromBytes(rs.getBytes("id")), UuidUtils.fromBytes(rs.getBytes("consent_id")),
                        UuidUtils.fromBytes(rs.getBytes("revision_id")), rs.getString("outcome"),
                        rs.getString("subject_id"), rs.getString("captured_by"),
                        CaptureMethod.valueOf(rs.getString("capture_method")),
                        rs.getString("capture_location"), rs.getString("authorization_reference"),
                        readMap(rs.getString("decision_data")), rs.getTimestamp("decided_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()), tenantId, UuidUtils.toBytes(consentId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        try {
            return json == null ? Map.of() : objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid consent decision JSON", exception);
        }
    }
}
