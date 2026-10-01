package vn.com.fis.consentcore.policy.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.policy.api.AuthorizationAssurancePolicyApi;
import vn.com.fis.consentcore.policy.api.RequiredAssurance;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Service
public class JdbcAuthorizationAssurancePolicyService implements AuthorizationAssurancePolicyApi {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JdbcAuthorizationAssurancePolicyService(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public RequiredAssurance resolve(String tenantId, String consentType, String clientId) {
        var now = toTimestamp(clock.instant());
        List<String> definitions = jdbc.query("""
                select definition
                  from policy_definition
                 where tenant_id = ?
                   and policy_type = 'AUTHORIZATION_ASSURANCE'
                   and status = 'ACTIVE'
                   and (effective_from is null or effective_from <= ?)
                   and (effective_until is null or effective_until > ?)
                   and coalesce(json_value(definition, '$.consentType'), '*') in ('*', ?)
                   and (json_value(definition, '$.clientId') is null
                        or json_value(definition, '$.clientId') in ('*', ?))
                 order by
                   case when json_value(definition, '$.clientId') = ? then 0
                        when json_value(definition, '$.clientId') = '*' then 1 else 2 end,
                   case when json_value(definition, '$.consentType') = ? then 0 else 1 end,
                   policy_version desc
                 fetch first 1 rows only
                """, (rs, n) -> rs.getString(1), tenantId, now, now, consentType, clientId, clientId, consentType);
        return definitions.isEmpty() ? RequiredAssurance.digitalDefault() : parse(definitions.getFirst());
    }

    private RequiredAssurance parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            boolean authenticationRequired = root.path("authenticationRequired").asBoolean(true);
            String requiredAcr = textOrNull(root.get("requiredAcr"));
            List<String> requiredAmr = new ArrayList<>();
            JsonNode amr = root.get("requiredAmr");
            if (amr != null && amr.isArray()) amr.forEach(value -> requiredAmr.add(value.asText()));
            Long maxAge = root.hasNonNull("maxAuthenticationAgeSeconds")
                    ? root.get("maxAuthenticationAgeSeconds").longValue() : null;
            boolean evidenceRequired = root.path("authenticationEvidenceRequired").asBoolean(false);
            boolean stepUpAllowed = root.path("stepUpAllowed").asBoolean(true);
            return new RequiredAssurance(authenticationRequired, requiredAcr, requiredAmr, maxAge,
                    evidenceRequired, stepUpAllowed);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid AUTHORIZATION_ASSURANCE policy definition", exception);
        }
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText().trim();
    }
}
