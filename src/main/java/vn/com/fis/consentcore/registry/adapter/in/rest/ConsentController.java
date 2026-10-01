package vn.com.fis.consentcore.registry.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.com.fis.consentcore.shared.security.RequestContextFactory;
import vn.com.fis.consentcore.registry.api.command.AuthorizeConsentCommand;
import vn.com.fis.consentcore.registry.api.command.CancelConsentCommand;
import vn.com.fis.consentcore.registry.api.command.CreateConsentRevisionCommand;
import vn.com.fis.consentcore.registry.api.command.ExpireConsentCommand;
import vn.com.fis.consentcore.registry.api.command.RegisterConsentCommand;
import vn.com.fis.consentcore.registry.api.command.RejectConsentCommand;
import vn.com.fis.consentcore.registry.api.command.RequestAuthorizationCommand;
import vn.com.fis.consentcore.registry.api.command.RevokeConsentCommand;
import vn.com.fis.consentcore.registry.api.command.SuspendConsentCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentHistoryResult;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;
import vn.com.fis.consentcore.registry.api.usecase.AuthorizeConsentUseCase;
import vn.com.fis.consentcore.registry.api.usecase.CancelConsentUseCase;
import vn.com.fis.consentcore.registry.api.usecase.CreateConsentRevisionUseCase;
import vn.com.fis.consentcore.registry.api.usecase.ExpireConsentUseCase;
import vn.com.fis.consentcore.registry.api.usecase.GetConsentHistoryUseCase;
import vn.com.fis.consentcore.registry.api.usecase.GetConsentUseCase;
import vn.com.fis.consentcore.registry.api.usecase.RegisterConsentUseCase;
import vn.com.fis.consentcore.registry.api.usecase.RejectConsentUseCase;
import vn.com.fis.consentcore.registry.api.usecase.RequestAuthorizationUseCase;
import vn.com.fis.consentcore.registry.api.usecase.RevokeConsentUseCase;
import vn.com.fis.consentcore.registry.api.usecase.SuspendConsentUseCase;
import vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel;
import vn.com.fis.consentcore.registry.domain.model.CaptureMethod;
import vn.com.fis.consentcore.registry.domain.model.EvidencePolicy;
import vn.com.fis.consentcore.content.api.ContentConstraint;
import vn.com.fis.consentcore.content.api.ContentPermission;
import vn.com.fis.consentcore.content.api.ContentResource;
import vn.com.fis.consentcore.content.api.ContentRevisionInput;
import vn.com.fis.consentcore.content.api.ContentRevisionResult;

@RestController
@RequestMapping("/api/v1/consents")
class ConsentController {
    private final RegisterConsentUseCase registerUseCase;
    private final CreateConsentRevisionUseCase createRevisionUseCase;
    private final RequestAuthorizationUseCase requestAuthorizationUseCase;
    private final AuthorizeConsentUseCase authorizeUseCase;
    private final RejectConsentUseCase rejectUseCase;
    private final SuspendConsentUseCase suspendUseCase;
    private final RevokeConsentUseCase revokeUseCase;
    private final ExpireConsentUseCase expireUseCase;
    private final CancelConsentUseCase cancelUseCase;
    private final GetConsentUseCase getConsentUseCase;
    private final GetConsentHistoryUseCase getHistoryUseCase;
    private final RequestContextFactory contextFactory;

    ConsentController(
            RegisterConsentUseCase registerUseCase,
            CreateConsentRevisionUseCase createRevisionUseCase,
            RequestAuthorizationUseCase requestAuthorizationUseCase,
            AuthorizeConsentUseCase authorizeUseCase,
            RejectConsentUseCase rejectUseCase,
            SuspendConsentUseCase suspendUseCase,
            RevokeConsentUseCase revokeUseCase,
            ExpireConsentUseCase expireUseCase,
            CancelConsentUseCase cancelUseCase,
            GetConsentUseCase getConsentUseCase,
            GetConsentHistoryUseCase getHistoryUseCase,
            RequestContextFactory contextFactory
    ) {
        this.registerUseCase = registerUseCase;
        this.createRevisionUseCase = createRevisionUseCase;
        this.requestAuthorizationUseCase = requestAuthorizationUseCase;
        this.authorizeUseCase = authorizeUseCase;
        this.rejectUseCase = rejectUseCase;
        this.suspendUseCase = suspendUseCase;
        this.revokeUseCase = revokeUseCase;
        this.expireUseCase = expireUseCase;
        this.cancelUseCase = cancelUseCase;
        this.getConsentUseCase = getConsentUseCase;
        this.getHistoryUseCase = getHistoryUseCase;
        this.contextFactory = contextFactory;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ConsentResult register(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RegisterConsentRequest body,
            HttpServletRequest request
    ) {
        return registerUseCase.register(new RegisterConsentCommand(
                idempotencyKey, body.externalRequestId(), body.consentType(), body.subjectId(),
                body.clientId(), body.purpose(), body.validFrom(), body.validUntil(),
                body.acquisitionChannel(), body.captureMethod(), body.sourceSystem(),
                body.externalConsentId(), body.evidencePolicy(), body.trustedSourceAuthorization(),
                body.trustedAuthorizationReference(), body.capturedAt(), body.capturedBy(),
                body.captureLocation(), body.importBatchReference(), toContent(body.content()),
                contextFactory.create(request)));
    }

    @PostMapping("/{consentId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    ContentRevisionResult createRevision(
            @PathVariable UUID consentId,
            @Valid @RequestBody ContentRequest body,
            HttpServletRequest request) {
        return createRevisionUseCase.createRevision(new CreateConsentRevisionCommand(
                consentId, toContent(body), contextFactory.create(request)));
    }

    @PostMapping("/{consentId}/request-authorization")
    ConsentResult requestAuthorization(
            @PathVariable UUID consentId,
            @RequestBody(required = false) RequestAuthorizationRequest body,
            HttpServletRequest request) {
        return requestAuthorizationUseCase.requestAuthorization(new RequestAuthorizationCommand(
                consentId, body == null ? null : body.reasonCode(), contextFactory.create(request)));
    }

    @PostMapping("/{consentId}/authorize")
    ConsentResult authorize(
            @PathVariable UUID consentId,
            @Valid @RequestBody AuthorizeConsentRequest body,
            HttpServletRequest request) {
        return authorizeUseCase.authorize(new AuthorizeConsentCommand(
                consentId, body.authorizationReference(), contextFactory.create(request)));
    }

    @PostMapping("/{consentId}/reject")
    ConsentResult reject(
            @PathVariable UUID consentId,
            @Valid @RequestBody ReasonRequest body,
            HttpServletRequest request) {
        return rejectUseCase.reject(new RejectConsentCommand(
                consentId, body.reasonCode(), body.reasonDetail(), contextFactory.create(request)));
    }

    @PostMapping("/{consentId}/suspend")
    ConsentResult suspend(
            @PathVariable UUID consentId,
            @Valid @RequestBody ReasonRequest body,
            HttpServletRequest request) {
        return suspendUseCase.suspend(new SuspendConsentCommand(
                consentId, body.reasonCode(), body.reasonDetail(), contextFactory.create(request)));
    }

    @PostMapping("/{consentId}/revoke")
    ConsentResult revoke(
            @PathVariable UUID consentId,
            @Valid @RequestBody ReasonRequest body,
            HttpServletRequest request) {
        return revokeUseCase.revoke(new RevokeConsentCommand(
                consentId, body.reasonCode(), body.reasonDetail(), contextFactory.create(request)));
    }

    @PostMapping("/{consentId}/expire")
    ConsentResult expire(@PathVariable UUID consentId, HttpServletRequest request) {
        return expireUseCase.expire(new ExpireConsentCommand(consentId, contextFactory.create(request)));
    }

    @PostMapping("/{consentId}/cancel")
    ConsentResult cancel(
            @PathVariable UUID consentId,
            @Valid @RequestBody ReasonRequest body,
            HttpServletRequest request) {
        return cancelUseCase.cancel(new CancelConsentCommand(
                consentId, body.reasonCode(), body.reasonDetail(), contextFactory.create(request)));
    }

    @GetMapping("/{consentId}")
    ConsentResult get(@PathVariable UUID consentId, HttpServletRequest request) {
        return getConsentUseCase.getConsent(contextFactory.tenantId(request), consentId);
    }

    @GetMapping("/{consentId}/history")
    List<ConsentHistoryResult> history(@PathVariable UUID consentId, HttpServletRequest request) {
        return getHistoryUseCase.getHistory(contextFactory.tenantId(request), consentId);
    }

    private static ContentRevisionInput toContent(ContentRequest content) {
        return new ContentRevisionInput(
                content.purposeCode(),
                content.permissions().stream()
                        .map(item -> new ContentPermission(item.permissionCode(), item.attributes())).toList(),
                content.resources().stream()
                        .map(item -> new ContentResource(item.resourceType(), item.resourceId(), item.attributes())).toList(),
                content.constraints().stream()
                        .map(item -> new ContentConstraint(item.constraintType(), item.operator(), item.value())).toList(),
                content.presentationSnapshot());
    }

    record RegisterConsentRequest(
            @Size(max = 200) String externalRequestId,
            @NotBlank @Size(max = 100) String consentType,
            @Size(max = 200) String subjectId,
            @NotBlank @Size(max = 200) String clientId,
            @Size(max = 500) String purpose,
            @NotNull Instant validFrom,
            @NotNull Instant validUntil,
            @NotNull AcquisitionChannel acquisitionChannel,
            @NotNull CaptureMethod captureMethod,
            @NotBlank @Size(max = 100) String sourceSystem,
            @Size(max = 200) String externalConsentId,
            @NotNull EvidencePolicy evidencePolicy,
            boolean trustedSourceAuthorization,
            @Size(max = 300) String trustedAuthorizationReference,
            Instant capturedAt,
            @Size(max = 200) String capturedBy,
            @Size(max = 300) String captureLocation,
            @Size(max = 200) String importBatchReference,
            @NotNull @Valid ContentRequest content) {
    }

    record ContentRequest(
            @NotBlank @Size(max = 100) String purposeCode,
            @NotEmpty @Size(max = 200) List<@Valid PermissionRequest> permissions,
            @NotNull @Size(max = 500) List<@Valid ResourceRequest> resources,
            @NotNull @Size(max = 200) List<@Valid ConstraintRequest> constraints,
            @NotNull Map<String, Object> presentationSnapshot) {
    }

    record PermissionRequest(
            @NotBlank @Size(max = 150) String permissionCode,
            @NotNull Map<String, Object> attributes) {
    }

    record ResourceRequest(
            @NotBlank @Size(max = 100) String resourceType,
            @NotBlank @Size(max = 300) String resourceId,
            @NotNull Map<String, Object> attributes) {
    }

    record ConstraintRequest(
            @NotBlank @Size(max = 100) String constraintType,
            @NotBlank @Size(max = 40) String operator,
            @NotNull Object value) {
    }

    record RequestAuthorizationRequest(@Size(max = 100) String reasonCode) {
    }

    record AuthorizeConsentRequest(@NotBlank @Size(max = 300) String authorizationReference) {
    }

    record ReasonRequest(
            @NotBlank @Size(max = 100) String reasonCode,
            @Size(max = 1000) String reasonDetail) {
    }
}
