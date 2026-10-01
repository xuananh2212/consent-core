package vn.com.fis.consentcore.policy.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.com.fis.consentcore.shared.security.RequestContextFactory;
import vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel;
import vn.com.fis.consentcore.registry.domain.model.EvidencePolicy;
import vn.com.fis.consentcore.policy.api.PolicyAdministrationApi;

@RestController
@RequestMapping("/api/v1/admin/policies")
public class PolicyAdminController {
    private final PolicyAdministrationApi administrationApi;
    private final RequestContextFactory contextFactory;

    public PolicyAdminController(PolicyAdministrationApi administrationApi, RequestContextFactory contextFactory) {
        this.administrationApi = administrationApi;
        this.contextFactory = contextFactory;
    }

    @PostMapping("/consent-types")
    @ResponseStatus(HttpStatus.CREATED)
    public UUID upsertPolicy(@Valid @RequestBody ConsentTypePolicyRequest body, HttpServletRequest request) {
        return administrationApi.upsertConsentTypePolicy(new PolicyAdministrationApi.ConsentTypePolicyCommand(
                body.consentType(), body.evidencePolicy(), body.maxValidityDays(), body.requireSubject(),
                body.allowTrustedAutoAuthorization(), body.allowedChannels(), contextFactory.create(request)));
    }

    @PostMapping("/trusted-sources")
    @ResponseStatus(HttpStatus.CREATED)
    public UUID registerTrustedSource(
            @Valid @RequestBody TrustedSourceRequest body, HttpServletRequest request) {
        return administrationApi.upsertTrustedSource(new PolicyAdministrationApi.TrustedSourceCommand(
                body.sourceSystem(), body.clientId(), body.allowedConsentTypes(), body.validFrom(),
                body.validUntil(), contextFactory.create(request)));
    }


    @PostMapping("/definitions")
    @ResponseStatus(HttpStatus.CREATED)
    public UUID publishPolicyDefinition(
            @Valid @RequestBody PolicyDefinitionRequest body, HttpServletRequest request) {
        return administrationApi.publishPolicyDefinition(new PolicyAdministrationApi.PolicyDefinitionCommand(
                body.policyCode(), body.policyVersion(), body.policyType(), body.status(), body.definition(),
                body.effectiveFrom(), body.effectiveUntil(), contextFactory.create(request)));
    }

    @GetMapping("/definitions")
    public List<PolicyAdministrationApi.PolicyDefinition> listPolicyDefinitions(
            @RequestParam(required = false) String policyType, HttpServletRequest request) {
        return administrationApi.listPolicyDefinitions(contextFactory.tenantId(request), policyType);
    }

    public record ConsentTypePolicyRequest(
            @NotBlank String consentType,
            @NotNull EvidencePolicy evidencePolicy,
            @Min(1) int maxValidityDays,
            boolean requireSubject,
            boolean allowTrustedAutoAuthorization,
            @NotNull List<AcquisitionChannel> allowedChannels) { }


    public record PolicyDefinitionRequest(
            @NotBlank String policyCode,
            @Min(1) int policyVersion,
            @NotBlank String policyType,
            @NotBlank String status,
            @NotNull Map<String, Object> definition,
            Instant effectiveFrom,
            Instant effectiveUntil) { }

    public record TrustedSourceRequest(
            @NotBlank String sourceSystem,
            String clientId,
            @NotNull List<String> allowedConsentTypes,
            Instant validFrom,
            Instant validUntil) { }
}
