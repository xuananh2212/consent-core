package vn.com.fis.consentcore.policy.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.policy.api.EligibilityProvider;
import vn.com.fis.consentcore.policy.api.PolicyEvaluationApi;
import vn.com.fis.consentcore.policy.api.RegistrationPolicyDecision;
import vn.com.fis.consentcore.policy.api.RegistrationPolicyRequest;
import vn.com.fis.consentcore.registry.domain.model.EvidencePolicy;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.helper.UuidUtils.toBytes;
import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Service
public class JdbcPolicyEvaluationService implements PolicyEvaluationApi {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int defaultMaxValidityDays;
    private final List<EligibilityProvider> eligibilityProviders;

    public JdbcPolicyEvaluationService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${consent.policy.default-max-validity-days:3650}") int defaultMaxValidityDays,
            List<EligibilityProvider> eligibilityProviders) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.defaultMaxValidityDays = defaultMaxValidityDays;
        this.eligibilityProviders = eligibilityProviders == null ? List.of() : List.copyOf(eligibilityProviders);
    }

    @Override
    @Transactional
    public RegistrationPolicyDecision evaluateRegistration(RegistrationPolicyRequest request) {
        validateRequired(request);
        List<PolicyRow> policies = jdbc.query("""
                        select id, evidence_policy, max_validity_days, require_subject,
                               allow_trusted_auto_authorization, allowed_channels
                          from consent_type_policy
                         where tenant_id = ? and consent_type = ? and active = 1
                        """, (rs, n) -> new PolicyRow(
                        UuidUtils.fromBytes(rs.getBytes(1)),
                        EvidencePolicy.valueOf(rs.getString("evidence_policy")),
                        rs.getInt("max_validity_days"),
                        rs.getBoolean("require_subject"),
                        rs.getBoolean("allow_trusted_auto_authorization"),
                        readStringList(rs.getString("allowed_channels"))),
                request.tenantId(), request.consentType());

        PolicyRow policy = policies.isEmpty() ? null : policies.getFirst();
        int maxDays = policy == null ? defaultMaxValidityDays : policy.maxValidityDays();
        EvidencePolicy effectiveEvidence = policy == null
                ? request.requestedEvidencePolicy() : policy.evidencePolicy();

        if (policy != null) {
            if (request.requestedEvidencePolicy() != policy.evidencePolicy()) {
                deny(request, policy.id(), "Requested evidence policy does not match tenant consent-type policy");
            }
            if (policy.requireSubject() && isBlank(request.subjectId())) {
                deny(request, policy.id(), "subjectId is required by policy");
            }
            if (!policy.allowedChannels().isEmpty()
                    && !policy.allowedChannels().contains(request.acquisitionChannel().name())) {
                deny(request, policy.id(), "Acquisition channel is not allowed by policy");
            }
        }

        long validitySeconds = Duration.between(request.validFrom(), request.validUntil()).getSeconds();
        if (validitySeconds > Duration.ofDays(maxDays).getSeconds()) {
            deny(request, policy == null ? null : policy.id(),
                    "Consent validity exceeds maximum of " + maxDays + " days");
        }

        evaluateEligibilityProviders(request, policy == null ? null : policy.id());

        boolean trustedAllowed = false;
        if (request.requestedTrustedAutoAuthorization()) {
            if (policy == null || !policy.allowTrustedAutoAuthorization()) {
                deny(request, policy == null ? null : policy.id(),
                        "Trusted-source auto-authorization is not enabled for this consent type");
            }
            if (effectiveEvidence != EvidencePolicy.NOT_REQUIRED) {
                deny(request, policy.id(), "Trusted auto-authorization requires evidence NOT_REQUIRED");
            }
            trustedAllowed = isTrustedSource(request, clock.instant());
            if (!trustedAllowed) {
                deny(request, policy.id(), "Source system/client is not registered as a trusted consent source");
            }
        }

        appendEvaluation(request, policy == null ? null : policy.id(), "ALLOW", List.of());
        return new RegistrationPolicyDecision(
                policy == null ? null : policy.id(), effectiveEvidence, trustedAllowed, maxDays);
    }

    private void evaluateEligibilityProviders(RegistrationPolicyRequest request, UUID policyId) {
        for (EligibilityProvider provider : eligibilityProviders) {
            EligibilityProvider.EligibilityResult result = provider.evaluate(request);
            if (result == null) {
                deny(request, policyId, "Eligibility provider returned no result: " + provider.providerName());
            }
            if (!result.eligible()) {
                String reason = result.reasons().isEmpty()
                        ? "Eligibility denied by " + provider.providerName()
                        : "Eligibility denied by " + provider.providerName() + ": " + String.join("; ", result.reasons());
                deny(request, policyId, reason);
            }
        }
    }

    private boolean isTrustedSource(RegistrationPolicyRequest request, Instant now) {
        Integer count = jdbc.queryForObject("""
                        select count(*)
                          from trusted_consent_source
                         where tenant_id = ? and source_system = ? and active = 1
                           and (client_id is null or client_id = ?)
                           and (valid_from is null or valid_from <= ?)
                           and (valid_until is null or valid_until > ?)
                           and (
                               not json_exists(allowed_consent_types, '$[0]')
                               or json_exists(
                                  allowed_consent_types,
                                  '$[*]?(@ == $consentType)'
                                  passing ? as "consentType"
                              )
                           )
                        """,
                Integer.class,
                request.tenantId(),
                request.sourceSystem(),
                request.clientId(),
                toTimestamp(now),
                toTimestamp(now),
                request.consentType()
        );

        return count != null && count > 0;
    }

    private void deny(RegistrationPolicyRequest request, UUID policyId, String reason) {
        appendEvaluation(request, policyId, "DENY", List.of(reason));
        throw new PolicyDeniedException(reason);
    }

    private void appendEvaluation(
            RegistrationPolicyRequest request, UUID policyId, String result, List<String> reasons) {
        jdbc.update("""
                        insert into policy_evaluation(
                            id, tenant_id, consent_id, operation, policy_id, result,
                            reasons, evaluated_context, evaluated_at)
                        values (?, ?, null, 'REGISTER_CONSENT', ?, ?, ?, ?, ?)
                        """,
                UuidUtils.toBytes(UUID.randomUUID()), request.tenantId(), UuidUtils.toBytes(policyId), result,
                json(reasons), json(Map.of(
                        "consentType", request.consentType(),
                        "clientId", request.clientId(),
                        "sourceSystem", request.sourceSystem(),
                        "acquisitionChannel", request.acquisitionChannel().name(),
                        "resourceTypes", request.resourceTypes(),
                        "riskContext", request.riskContext(),
                        "eligibilityProviders", eligibilityProviders.stream()
                                .map(EligibilityProvider::providerName).toList())),
                toTimestamp(clock.instant()));
    }

    private void validateRequired(RegistrationPolicyRequest request) {
        if (request == null || isBlank(request.tenantId()) || isBlank(request.consentType())
                || isBlank(request.clientId()) || isBlank(request.sourceSystem())
                || request.acquisitionChannel() == null || request.requestedEvidencePolicy() == null
                || request.validFrom() == null || request.validUntil() == null) {
            throw new PolicyDeniedException("Incomplete policy evaluation context");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize policy evaluation", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(String json) {
        try {
            return json == null ? List.of() : objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid policy JSON", exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record PolicyRow(
            UUID id,
            EvidencePolicy evidencePolicy,
            int maxValidityDays,
            boolean requireSubject,
            boolean allowTrustedAutoAuthorization,
            List<String> allowedChannels) {
    }
}
