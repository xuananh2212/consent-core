package vn.com.fis.consentcore.registry.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.com.fis.consentcore.registry.api.authz.InteractionProvenance;
import vn.com.fis.consentcore.registry.api.authz.VerifiedAuthenticationContext;
import vn.com.fis.consentcore.registry.application.internal.port.AuthenticationEvidencePort;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Repository
class JdbcAuthenticationEvidenceAdapter implements AuthenticationEvidencePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcAuthenticationEvidenceAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(AuthenticationEvidenceRecord record) {
        jdbc.update("""
                insert into consent_authentication_evidence(
                    id, tenant_id, consent_id, decision_id, subject_ref,
                    source_system, source_interaction_ref, authorization_server_ref, as_transaction_ref,
                    issuer, authentication_time, acr, amr, evidence_ref, evidence_hash,
                    verification_profile, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UuidUtils.toBytes(record.id()), record.tenantId(), UuidUtils.toBytes(record.consentId()),
                UuidUtils.toBytes(record.decisionId()), record.subjectRef(),
                record.interaction().sourceSystem(), record.interaction().sourceInteractionRef(),
                record.interaction().authorizationServerRef(), record.interaction().asTransactionRef(),
                record.authentication().issuer(), toTimestamp(record.authentication().authenticationTime()),
                record.authentication().acr(), json(record.authentication().amr()),
                record.authentication().evidenceRef(), record.authentication().evidenceHash(),
                record.authentication().verificationProfile(), toTimestamp(record.createdAt()));
    }

    @Override
    public Optional<AuthenticationEvidenceRecord> findMatching(
            String tenantId, UUID consentId, UUID decisionId,
            String sourceInteractionRef, String authorizationServerRef, String asTransactionRef) {
        StringBuilder sql = new StringBuilder("""
                select id, tenant_id, consent_id, decision_id, subject_ref,
                       source_system, source_interaction_ref, authorization_server_ref, as_transaction_ref,
                       issuer, authentication_time, acr, amr, evidence_ref, evidence_hash,
                       verification_profile, created_at
                  from consent_authentication_evidence
                 where tenant_id = ? and consent_id = ? and decision_id = ?
                   and source_interaction_ref = ?
                """);

        List<Object> args = new ArrayList<>(List.of(
                tenantId, UuidUtils.toBytes(consentId), UuidUtils.toBytes(decisionId), sourceInteractionRef));

        if (authorizationServerRef != null && !authorizationServerRef.isBlank()) {
            sql.append(" and authorization_server_ref = ?");
            args.add(authorizationServerRef);
        }

        if (asTransactionRef != null && !asTransactionRef.isBlank()) {
            sql.append(" and as_transaction_ref = ?");
            args.add(asTransactionRef);
        }

        sql.append(" order by created_at desc fetch first 1 rows only");

        List<AuthenticationEvidenceRecord> rows = jdbc.query(sql.toString(), (rs, n) -> new AuthenticationEvidenceRecord(
                UuidUtils.fromBytes(rs.getBytes("id")), rs.getString("tenant_id"),
                UuidUtils.fromBytes(rs.getBytes("consent_id")), UuidUtils.fromBytes(rs.getBytes("decision_id")),
                rs.getString("subject_ref"),
                new InteractionProvenance(
                        rs.getString("source_system"), rs.getString("source_interaction_ref"),
                        rs.getString("authorization_server_ref"), rs.getString("as_transaction_ref")),
                new VerifiedAuthenticationContext(
                        rs.getString("subject_ref"), rs.getString("issuer"),
                        rs.getTimestamp("authentication_time").toInstant(), rs.getString("acr"),
                        readStringList(rs.getString("amr")), rs.getString("evidence_ref"),
                        rs.getString("evidence_hash"), rs.getString("verification_profile")),
                rs.getTimestamp("created_at").toInstant()),
        args.toArray());

        return rows.stream().findFirst();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Authentication evidence is not valid JSON", exception);
        }
    }

    private List<String> readStringList(String json) {
        try {
            return json == null ? List.of() : objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid AMR JSON", exception);
        }
    }
}
