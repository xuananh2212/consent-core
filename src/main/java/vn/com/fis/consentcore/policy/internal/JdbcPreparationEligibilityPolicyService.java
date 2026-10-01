package vn.com.fis.consentcore.policy.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.enrichment.api.DataPurpose;
import vn.com.fis.consentcore.enrichment.api.ResolvedRequirementData;
import vn.com.fis.consentcore.policy.api.PreparationEligibilityPolicyApi;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

/**
 * Reference implementation for the v0.5 post-authentication eligibility gate.
 *
 * A POLICY data requirement becomes an actual boolean gate only when its requirement configuration contains
 * an `eligibility` object. The provider/mapping adapter remains responsible for normalizing bank-specific data
 * into the configured boolean fact (default field: `eligible`). This intentionally avoids embedding an arbitrary
 * expression/rule engine in Consent Core.
 */
@Service
public class JdbcPreparationEligibilityPolicyService implements PreparationEligibilityPolicyApi {
    private static final String OPERATION = "PREPARE_AUTHORIZATION";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JdbcPreparationEligibilityPolicyService(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PreparationEligibilityDecision evaluate(PreparationEligibilityRequest request) {
        if (request == null || request.context() == null) {
            throw new IllegalArgumentException("preparation eligibility request/context must not be null");
        }

        List<String> evaluatedRequirements = new ArrayList<>();
        for (ResolvedRequirementData fact : request.policyFacts()) {
            if (fact.purpose() != DataPurpose.POLICY) continue;
            EligibilityGate gate = gateConfiguration(fact);
            if (gate == null || !gate.enabled()) continue;

            evaluatedRequirements.add(fact.requirementCode());
            Object value = fact.data().get(gate.field());
            if (!(value instanceof Boolean eligible)) {
                throw new BusinessException(
                        ErrorCode.CONSENT_DATA_REQUIREMENT_UNRESOLVED,
                        "Configured POLICY eligibility fact is missing a boolean outcome",
                        Map.of(
                                "requirementCode", fact.requirementCode(),
                                "dataType", fact.dataType(),
                                "expectedField", gate.field()));
            }

            if (!eligible) {
                PreparationEligibilityDecision denied = PreparationEligibilityDecision.deny(
                        gate.reasonCode(), gate.messageKey(), gate.retryable(), fact.requirementCode());
                appendEvaluation(request, "DENY", List.of(gate.reasonCode()), evaluatedRequirements, fact, denied);
                return denied;
            }
        }

        PreparationEligibilityDecision allowed = PreparationEligibilityDecision.allow();
        appendEvaluation(request, "ALLOW", List.of(), evaluatedRequirements, null, allowed);
        return allowed;
    }

    private EligibilityGate gateConfiguration(ResolvedRequirementData fact) {
        Object raw = fact.requirementConfiguration().get("eligibility");
        if (!(raw instanceof Map<?, ?> map)) return null;
        boolean enabled = booleanValue(map.get("enabled"), true);
        String field = textValue(map.get("field"), "eligible");
        String reasonCode = textValue(map.get("reasonCode"), "CONSENT_ELIGIBILITY_DENIED");
        String messageKey = textValue(map.get("messageKey"), "consent.eligibility.denied");
        boolean retryable = booleanValue(map.get("retryable"), false);
        return new EligibilityGate(enabled, field, reasonCode, messageKey, retryable);
    }

    private void appendEvaluation(
            PreparationEligibilityRequest request,
            String result,
            List<String> reasons,
            List<String> evaluatedRequirements,
            ResolvedRequirementData deniedFact,
            PreparationEligibilityDecision decision) {
        Map<String,Object> context = new LinkedHashMap<>();
        context.put("consentType", request.consentType());
        context.put("clientId", request.clientId());
        context.put("effectiveScopes", request.effectiveScopes());
        context.put("policyRequirements", List.copyOf(evaluatedRequirements));
        if (deniedFact != null) {
            context.put("deniedRequirementCode", deniedFact.requirementCode());
            context.put("dataType", deniedFact.dataType());
            context.put("providerCode", deniedFact.providerCode());
            context.put("normalizedDataHash", deniedFact.normalizedDataHash());
        }
        if (!decision.allowed()) {
            context.put("reasonCode", decision.reasonCode());
            context.put("messageKey", decision.messageKey());
            context.put("retryable", decision.retryable());
        }

        jdbc.update("""
                insert into policy_evaluation(
                    id, tenant_id, consent_id, operation, policy_id, result,
                    reasons, evaluated_context, evaluated_at)
                values (?, ?, ?, ?, null, ?, ?, ?, ?)
                """, UuidUtils.toBytes(UUID.randomUUID()), request.context().tenantId(),
                UuidUtils.toBytes(request.consentId()), OPERATION, result,
                json(reasons), json(context), toTimestamp(clock.instant()));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize preparation policy evaluation", exception);
        }
    }

    private static String textValue(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private record EligibilityGate(
            boolean enabled,
            String field,
            String reasonCode,
            String messageKey,
            boolean retryable) { }
}
