package vn.com.fis.consentcore.registry.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.registry.api.query.ConsentSearchCriteria;
import vn.com.fis.consentcore.registry.api.query.PageResult;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;
import vn.com.fis.consentcore.registry.api.usecase.SearchConsentsUseCase;
import vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel;
import vn.com.fis.consentcore.registry.domain.model.CaptureMethod;
import vn.com.fis.consentcore.registry.domain.model.ConsentStatus;
import vn.com.fis.consentcore.registry.domain.model.EvidencePolicy;
import vn.com.fis.consentcore.registry.domain.model.EvidenceStatus;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Repository
class JdbcConsentSearchAdapter implements SearchConsentsUseCase {
    private static final String SELECT_COLUMNS = """
            select id, tenant_id, external_request_id, consent_type, subject_id, client_id, purpose,
                   status, valid_from, valid_until, acquisition_channel, capture_method, source_system,
                   external_consent_id, evidence_policy, evidence_status, trusted_source_authorization,
                   authorization_reference, captured_at, captured_by, capture_location, import_batch_reference,
                   current_revision_id, current_revision_no, decision_id, evidence_bundle_id,
                   created_at, updated_at, created_by, updated_by, version
              from consent
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcConsentSearchAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ConsentResult> search(ConsentSearchCriteria criteria) {
        StringBuilder where = new StringBuilder(" where tenant_id = :tenantId");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantId", criteria.tenantId());
        add(where, parameters, "status", criteria.status() == null ? null : criteria.status().name());
        add(where, parameters, "consent_type", "consentType", criteria.consentType());
        add(where, parameters, "subject_id", "subjectId", criteria.subjectId());
        add(where, parameters, "client_id", "clientId", criteria.clientId());
        add(where, parameters, "source_system", "sourceSystem", criteria.sourceSystem());
        add(where, parameters, "external_consent_id", "externalConsentId", criteria.externalConsentId());
        if (criteria.createdFrom() != null) {
            where.append(" and created_at >= :createdFrom");
            parameters.put("createdFrom", toTimestamp(criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            where.append(" and created_at < :createdTo");
            parameters.put("createdTo", toTimestamp(criteria.createdTo()));
        }
        long total = jdbc.queryForObject("select count(*) from consent" + where, parameters, Long.class);
        parameters.put("limit", criteria.size());
        parameters.put("offset", criteria.page() * criteria.size());
        List<ConsentResult> items = jdbc.query(
                SELECT_COLUMNS + where + " order by created_at desc, id desc offset :offset rows fetch next :limit rows only",
                parameters, JdbcConsentSearchAdapter::map);
        int totalPages = total == 0 ? 0 : (int) ((total + criteria.size() - 1) / criteria.size());
        return new PageResult<>(items, criteria.page(), criteria.size(), total, totalPages);
    }

    private static void add(StringBuilder where, Map<String, Object> parameters, String column, Object value) {
        add(where, parameters, column, column, value);
    }

    private static void add(
            StringBuilder where, Map<String, Object> parameters, String column, String parameter, Object value) {
        if (value == null || value.toString().isBlank()) return;
        where.append(" and ").append(column).append(" = :").append(parameter);
        parameters.put(parameter, value.toString().trim());
    }

    private static ConsentResult map(ResultSet rs, int rowNum) throws SQLException {
        return new ConsentResult(
                UuidUtils.fromBytes(rs.getBytes("id")), rs.getString("tenant_id"), rs.getString("external_request_id"),
                rs.getString("consent_type"), rs.getString("subject_id"), rs.getString("client_id"),
                rs.getString("purpose"), ConsentStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("valid_from").toInstant(), rs.getTimestamp("valid_until").toInstant(),
                AcquisitionChannel.valueOf(rs.getString("acquisition_channel")),
                CaptureMethod.valueOf(rs.getString("capture_method")), rs.getString("source_system"),
                rs.getString("external_consent_id"), EvidencePolicy.valueOf(rs.getString("evidence_policy")),
                EvidenceStatus.valueOf(rs.getString("evidence_status")),
                rs.getBoolean("trusted_source_authorization"), rs.getString("authorization_reference"),
                instant(rs, "captured_at"), rs.getString("captured_by"), rs.getString("capture_location"),
                rs.getString("import_batch_reference"), UuidUtils.fromBytes(rs.getBytes("current_revision_id")),
                rs.getObject("current_revision_no") == null ? null : rs.getInt("current_revision_no"),
                UuidUtils.fromBytes(rs.getBytes("decision_id")),
                UuidUtils.fromBytes(rs.getBytes("evidence_bundle_id")), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), rs.getString("created_by"),
                rs.getString("updated_by"), rs.getLong("version"));
    }

    private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
