package vn.com.fis.consentcore.registry.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.fis.consentcore.content.api.ConsentContentApi;
import vn.com.fis.consentcore.content.api.ContentRevisionResult;
import vn.com.fis.consentcore.registry.api.authz.AuthorizationValidationRequest;
import vn.com.fis.consentcore.registry.api.authz.AuthorizationValidationResult;
import vn.com.fis.consentcore.registry.api.authz.CommandResult;
import vn.com.fis.consentcore.registry.api.authz.ConsentAuthorizationContext;
import vn.com.fis.consentcore.registry.api.authz.ConsentPreparationResult;
import vn.com.fis.consentcore.registry.api.authz.InteractionProvenance;
import vn.com.fis.consentcore.registry.api.authz.VerifiedAuthenticationContext;
import vn.com.fis.consentcore.registry.api.command.AuthorizeConsentCommand;
import vn.com.fis.consentcore.registry.api.command.PrepareAuthorizationCommand;
import vn.com.fis.consentcore.registry.api.command.RejectConsentCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;
import vn.com.fis.consentcore.registry.api.usecase.AuthorizeConsentFromFlowUseCase;
import vn.com.fis.consentcore.registry.api.usecase.GetConsentAuthorizationContextUseCase;
import vn.com.fis.consentcore.registry.api.usecase.GetConsentCommandResultUseCase;
import vn.com.fis.consentcore.registry.api.usecase.PrepareConsentAuthorizationUseCase;
import vn.com.fis.consentcore.registry.api.usecase.RejectConsentFromFlowUseCase;
import vn.com.fis.consentcore.registry.api.usecase.ValidateConsentAuthorizationUseCase;
import vn.com.fis.consentcore.shared.security.RequestContextFactory;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

/** Internal service-to-service contract used by Consent Flow Manager. */
@RestController
@RequestMapping("/internal/v1/consents")
public class FlowManagerConsentController {
    private final GetConsentAuthorizationContextUseCase authorizationContextUseCase;
    private final PrepareConsentAuthorizationUseCase prepareUseCase;
    private final AuthorizeConsentFromFlowUseCase authorizeUseCase;
    private final RejectConsentFromFlowUseCase rejectUseCase;
    private final ValidateConsentAuthorizationUseCase validationUseCase;
    private final GetConsentCommandResultUseCase commandResultUseCase;
    private final ConsentContentApi contentApi;
    private final RequestContextFactory contextFactory;

    public FlowManagerConsentController(
            GetConsentAuthorizationContextUseCase authorizationContextUseCase,
            PrepareConsentAuthorizationUseCase prepareUseCase,
            AuthorizeConsentFromFlowUseCase authorizeUseCase,
            RejectConsentFromFlowUseCase rejectUseCase,
            ValidateConsentAuthorizationUseCase validationUseCase,
            GetConsentCommandResultUseCase commandResultUseCase,
            ConsentContentApi contentApi,
            RequestContextFactory contextFactory) {
        this.authorizationContextUseCase = authorizationContextUseCase;
        this.prepareUseCase = prepareUseCase;
        this.authorizeUseCase = authorizeUseCase;
        this.rejectUseCase = rejectUseCase;
        this.validationUseCase = validationUseCase;
        this.commandResultUseCase = commandResultUseCase;
        this.contentApi = contentApi;
        this.contextFactory = contextFactory;
    }

    @GetMapping("/{consentId}/authorization-context")
    public ConsentAuthorizationContext authorizationContext(
            @PathVariable UUID consentId, HttpServletRequest request) {
        return authorizationContextUseCase.getAuthorizationContext(contextFactory.tenantId(request), consentId);
    }

    @PostMapping("/{consentId}/prepare-authorization")
    public ConsentPreparationResult prepareAuthorization(
            @PathVariable UUID consentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PrepareAuthorizationRequest body,
            HttpServletRequest request) {
        return prepareUseCase.prepareAuthorization(new PrepareAuthorizationCommand(
                consentId, idempotencyKey, body.clientId(), body.scopes(), body.subjectRef(), body.selectionContextHash(),
                body.selections(), body.interactionMode(), contextFactory.create(request)));
    }

    @GetMapping("/{consentId}/presentation")
    public ContentRevisionResult presentation(
            @PathVariable UUID consentId,
            @RequestParam(required = false) Integer revision,
            HttpServletRequest request) {
        String tenantId = contextFactory.tenantId(request);
        ConsentAuthorizationContext authorizationContext = authorizationContextUseCase.getAuthorizationContext(tenantId, consentId);
        if (!authorizationContext.presentationReady()) {
            throw new BusinessException(ErrorCode.CONSENT_CONTENT_NOT_FINALIZED,
                    "Consent preparation must complete before the final decision presentation can be read");
        }
        if (revision == null) return contentApi.getCurrent(tenantId, consentId);
        return contentApi.list(tenantId, consentId).stream()
                .filter(item -> item.revisionNo() == revision)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Requested consent revision does not exist"));
    }

    @PostMapping("/{consentId}/authorize")
    public ConsentResult authorize(
            @PathVariable UUID consentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody FlowAuthorizeRequest body,
            HttpServletRequest request) {
        return authorizeUseCase.authorizeFromFlow(new AuthorizeConsentCommand(
                consentId, body.authorizationReference(), idempotencyKey, body.expectedVersion(),
                body.expectedRevisionNo(), body.expectedContentHash(), body.clientId(), body.subjectRef(),
                body.authentication().toDomain(), body.interaction().toDomain(), body.decidedAt(),
                contextFactory.create(request)));
    }

    @PostMapping("/{consentId}/reject")
    public ConsentResult reject(
            @PathVariable UUID consentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody FlowRejectRequest body,
            HttpServletRequest request) {
        return rejectUseCase.rejectFromFlow(new RejectConsentCommand(
                consentId, body.reasonCode(), body.reasonDetail(), idempotencyKey, body.expectedVersion(),
                body.expectedRevisionNo(), body.expectedContentHash(), body.clientId(), body.subjectRef(),
                body.authentication().toDomain(), body.interaction().toDomain(), body.decidedAt(),
                contextFactory.create(request)));
    }

    @GetMapping("/{consentId}/authorization-validation")
    public AuthorizationValidationResult validateAuthorization(
            @PathVariable UUID consentId,
            @RequestParam String clientId,
            @RequestParam String subjectRef,
            @RequestParam Integer revisionNo,
            @RequestParam String contentHash,
            @RequestParam String sourceInteractionRef,
            @RequestParam(required = false) String authorizationServerRef,
            @RequestParam(required = false) String asTransactionRef,
            HttpServletRequest request) {
        return validationUseCase.validateAuthorization(contextFactory.tenantId(request),
                new AuthorizationValidationRequest(consentId, clientId, subjectRef, revisionNo, contentHash,
                        sourceInteractionRef, authorizationServerRef, asTransactionRef));
    }

    @GetMapping("/command-results")
    public ResponseEntity<CommandResult> commandResult(
            @RequestParam String operation,
            @RequestParam String idempotencyKey,
            HttpServletRequest request) {
        return commandResultUseCase.getCommandResult(contextFactory.tenantId(request), operation, idempotencyKey)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record PrepareAuthorizationRequest(
            @NotBlank @Size(max = 200) String clientId,
            @Size(max = 50) List<@NotBlank @Size(max = 200) String> scopes,
            @Size(max = 200) String subjectRef,
            @Size(max = 128) String selectionContextHash,
            Map<String, List<String>> selections,
            @Pattern(regexp = "BROWSER_REDIRECT|APP_TO_APP_REDIRECT|CIBA") String interactionMode) {
        public PrepareAuthorizationRequest {
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
            selections = selections == null ? Map.of() : Map.copyOf(selections);
        }
    }

    public record FlowAuthorizeRequest(
            @NotBlank @Size(max = 300) String authorizationReference,
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull @Positive Integer expectedRevisionNo,
            @NotBlank @Size(max = 200) String expectedContentHash,
            @NotBlank @Size(max = 200) String clientId,
            @NotBlank @Size(max = 200) String subjectRef,
            @NotNull @Valid AuthenticationRequest authentication,
            @NotNull @Valid InteractionRequest interaction,
            Instant decidedAt) { }

    public record FlowRejectRequest(
            @NotBlank @Size(max = 100) String reasonCode,
            @Size(max = 1000) String reasonDetail,
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull @Positive Integer expectedRevisionNo,
            @NotBlank @Size(max = 200) String expectedContentHash,
            @NotBlank @Size(max = 200) String clientId,
            @NotBlank @Size(max = 200) String subjectRef,
            @NotNull @Valid AuthenticationRequest authentication,
            @NotNull @Valid InteractionRequest interaction,
            Instant decidedAt) { }

    public record AuthenticationRequest(
            @NotBlank @Size(max = 200) String subjectRef,
            @NotBlank @Size(max = 300) String issuer,
            @NotNull Instant authenticationTime,
            @Size(max = 300) String acr,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 100) String> amr,
            @Size(max = 500) String evidenceRef,
            @Size(max = 200) String evidenceHash,
            @NotBlank @Size(max = 200) String verificationProfile) {
        VerifiedAuthenticationContext toDomain() {
            return new VerifiedAuthenticationContext(subjectRef, issuer, authenticationTime, acr, amr,
                    evidenceRef, evidenceHash, verificationProfile);
        }
    }

    public record InteractionRequest(
            @NotBlank @Size(max = 100) String sourceSystem,
            @NotBlank @Size(max = 200) String sourceInteractionRef,
            @Size(max = 150) String authorizationServerRef,
            @Size(max = 300) String asTransactionRef) {
        InteractionProvenance toDomain() {
            return new InteractionProvenance(sourceSystem, sourceInteractionRef,
                    authorizationServerRef, asTransactionRef);
        }
    }
}
