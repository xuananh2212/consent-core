package vn.com.fis.consentcore.registry.application.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.audit.api.AuditRecord;
import vn.com.fis.consentcore.audit.api.AuditWriter;
import vn.com.fis.consentcore.registry.api.command.*;
import vn.com.fis.consentcore.registry.api.result.ConsentHistoryResult;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;
import vn.com.fis.consentcore.registry.api.usecase.*;
import vn.com.fis.consentcore.registry.application.internal.exception.ConsentAlreadyExistsException;
import vn.com.fis.consentcore.registry.application.internal.exception.ConsentNotFoundException;
import vn.com.fis.consentcore.registry.application.internal.exception.IdempotencyKeyReuseException;
import vn.com.fis.consentcore.registry.application.internal.port.ConsentHistoryPort;
import vn.com.fis.consentcore.registry.application.internal.port.ConsentRepositoryPort;
import vn.com.fis.consentcore.registry.application.internal.port.DecisionCapturePort;
import vn.com.fis.consentcore.registry.application.internal.port.IdempotencyPort;
import vn.com.fis.consentcore.registry.application.internal.port.IdempotencyRecord;
import vn.com.fis.consentcore.registry.domain.model.Consent;
import vn.com.fis.consentcore.registry.domain.model.ConsentRegistrationData;
import vn.com.fis.consentcore.content.api.ConsentContentApi;
import vn.com.fis.consentcore.content.api.ContentRevisionResult;
import vn.com.fis.consentcore.extension.api.ExtensionContext;
import vn.com.fis.consentcore.extension.api.ExtensionEngine;
import vn.com.fis.consentcore.extension.api.ExtensionPoint;
import vn.com.fis.consentcore.outbox.api.OutboxWriter;
import vn.com.fis.consentcore.policy.api.PolicyEvaluationApi;
import vn.com.fis.consentcore.policy.api.RegistrationPolicyRequest;
import vn.com.fis.consentcore.shared.api.CommandContext;
import vn.com.fis.consentcore.shared.domain.DomainEvent;

@Service
public class ConsentApplicationService implements
        RegisterConsentUseCase,
        CreateConsentRevisionUseCase,
        RequestAuthorizationUseCase,
        AuthorizeConsentUseCase,
        RejectConsentUseCase,
        SuspendConsentUseCase,
        RevokeConsentUseCase,
        ExpireConsentUseCase,
        CancelConsentUseCase,
        GetConsentUseCase,
        GetConsentHistoryUseCase,
        LinkEvidenceBundleUseCase,
        UpdateEvidenceStatusUseCase,
        BindConsentSubjectUseCase {

    private static final String IDEMPOTENCY_OPERATION_REGISTER = "REGISTER_CONSENT";
    private final ConsentRepositoryPort consentRepository;
    private final ConsentHistoryPort historyPort;
    private final IdempotencyPort idempotencyPort;
    private final DecisionCapturePort decisionCapturePort;
    private final ConsentContentApi contentApi;
    private final PolicyEvaluationApi policyApi;
    private final ExtensionEngine extensionEngine;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ConsentRequestHasher requestHasher;
    private final Clock clock;

    public ConsentApplicationService(
            ConsentRepositoryPort consentRepository,
            ConsentHistoryPort historyPort,
            IdempotencyPort idempotencyPort,
            DecisionCapturePort decisionCapturePort,
            ConsentContentApi contentApi,
            PolicyEvaluationApi policyApi,
            ExtensionEngine extensionEngine,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            ConsentRequestHasher requestHasher,
            Clock clock
    ) {
        this.consentRepository = consentRepository;
        this.historyPort = historyPort;
        this.idempotencyPort = idempotencyPort;
        this.decisionCapturePort = decisionCapturePort;
        this.contentApi = contentApi;
        this.policyApi = policyApi;
        this.extensionEngine = extensionEngine;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.requestHasher = requestHasher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ConsentResult register(RegisterConsentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        CommandContext context = requireContext(command.context());
        String idempotencyKey = requireText(command.idempotencyKey(), "idempotencyKey");
        String requestHash = requestHasher.hash(command);
        idempotencyPort.acquireTransactionLock(
                context.tenantId(), idempotencyKey, IDEMPOTENCY_OPERATION_REGISTER);

        var prior = idempotencyPort.find(context.tenantId(), idempotencyKey, IDEMPOTENCY_OPERATION_REGISTER);
        if (prior.isPresent()) {
            if (!prior.get().requestHash().equals(requestHash)) {
                throw new IdempotencyKeyReuseException(idempotencyKey);
            }
            return toResult(load(context.tenantId(), prior.get().consentId()));
        }

        if (hasText(command.externalConsentId())) {
            consentRepository.findByExternalReference(
                    context.tenantId(), command.sourceSystem(), command.externalConsentId())
                    .ifPresent(existing -> {
                        throw new ConsentAlreadyExistsException(
                                context.tenantId(), command.sourceSystem(),
                                command.externalConsentId(), existing.id());
                    });
        }

        ExtensionContext registrationContext = extensionContext(
                null, command.consentType(), command.clientId(), command.acquisitionChannel(), command.captureMethod(), null, context,
                Map.of("externalRequestId", nullToEmpty(command.externalRequestId())));
        if (isExternalAcquisition(command.acquisitionChannel())) {
            extensionEngine.execute(ExtensionPoint.BEFORE_EXTERNAL_CONSENT_REGISTRATION, registrationContext);
        }
        extensionEngine.execute(ExtensionPoint.BEFORE_CONSENT_REGISTRATION, registrationContext);

        var policy = policyApi.evaluateRegistration(new RegistrationPolicyRequest(
                context.tenantId(), command.consentType(), command.subjectId(), command.clientId(),
                command.sourceSystem(), command.acquisitionChannel(), command.evidencePolicy(),
                command.trustedSourceAuthorization(), command.validFrom(), command.validUntil(),
                command.content().resources().stream().map(vn.com.fis.consentcore.content.api.ContentResource::resourceType).toList(),
                Map.of()));

        Instant now = clock.instant();
        Consent consent = Consent.register(new ConsentRegistrationData(
                context.tenantId(), command.externalRequestId(), command.consentType(), command.subjectId(),
                command.clientId(), command.purpose(), command.validFrom(), command.validUntil(),
                command.acquisitionChannel(), command.captureMethod(), command.sourceSystem(),
                command.externalConsentId(), policy.evidencePolicy(),
                policy.trustedAutoAuthorizationAllowed(), command.capturedAt(), command.capturedBy(),
                command.captureLocation(), command.importBatchReference()), context.actorId(), now);

        // First flush creates the parent row required by content revision foreign keys.
        consentRepository.save(consent);
        ExtensionContext buildContext = extensionContext(
                consent.id(), consent.consentType(), consent.clientId(), consent.acquisitionChannel(), consent.captureMethod(), null,
                context, Map.of("initialRevision", true));
        extensionEngine.execute(ExtensionPoint.BEFORE_CONSENT_BUILD, buildContext);
        ContentRevisionResult revision = contentApi.createInitialFinalized(
                context.tenantId(), consent.id(), command.content(), context.actorId(), now);
        consent.linkContentRevision(revision.id(), revision.revisionNo(), context.actorId(), now);
        extensionEngine.execute(ExtensionPoint.AFTER_CONSENT_BUILD,
                extensionContext(consent.id(), consent.consentType(), consent.clientId(), consent.acquisitionChannel(), consent.captureMethod(), null,
                        context, Map.of("revisionNo", revision.revisionNo(), "contentHash", revision.contentHash())));

        if (policy.trustedAutoAuthorizationAllowed()) {
            consent.requestAuthorization("TRUSTED_SOURCE_AUTO_AUTHORIZATION", context.actorId(), now);
            UUID decisionId = captureDecision(
                    consent, "AUTHORIZED", command.trustedAuthorizationReference(), context, now);
            consent.recordDecision(decisionId, revision.id(), "AUTHORIZED", context.actorId(), now);
            ExtensionContext grantContext = extensionContext(
                    consent.id(), consent.consentType(), consent.clientId(), consent.acquisitionChannel(), consent.captureMethod(), null,
                    context, Map.of("trustedSource", true));
            extensionEngine.execute(ExtensionPoint.BEFORE_CONSENT_GRANT, grantContext);
            extensionEngine.execute(ExtensionPoint.BEFORE_CONSENT_AUTHORIZATION, grantContext);
            consent.authorize(requireText(command.trustedAuthorizationReference(),
                    "trustedAuthorizationReference"), context.actorId(), now);
        }

        Consent persisted = persistChanges(consent, context);
        idempotencyPort.save(new IdempotencyRecord(
                UUID.randomUUID(), context.tenantId(), idempotencyKey, IDEMPOTENCY_OPERATION_REGISTER,
                requestHash, persisted.id(), now, now.plus(Duration.ofHours(24))));
        appendAudit("REGISTER_CONSENT", persisted, context, Map.of(
                "autoAuthorized", policy.trustedAutoAuthorizationAllowed(),
                "finalStatus", persisted.status().name(),
                "revisionNo", revision.revisionNo(),
                "contentHash", revision.contentHash()));
        ExtensionContext afterRegistrationContext = extensionContext(
                persisted.id(), persisted.consentType(), persisted.clientId(), persisted.acquisitionChannel(), persisted.captureMethod(), null,
                context, Map.of("status", persisted.status().name()));
        extensionEngine.execute(ExtensionPoint.AFTER_CONSENT_REGISTRATION, afterRegistrationContext);
        if (isExternalAcquisition(persisted.acquisitionChannel())) {
            extensionEngine.execute(ExtensionPoint.AFTER_EXTERNAL_CONSENT_REGISTRATION, afterRegistrationContext);
        }
        if (persisted.status().name().equals("AUTHORIZED")) {
            ExtensionContext grantedContext = extensionContext(
                    persisted.id(), persisted.consentType(), persisted.clientId(), persisted.acquisitionChannel(), persisted.captureMethod(), null,
                    context, Map.of("trustedSource", true));
            extensionEngine.execute(ExtensionPoint.AFTER_CONSENT_AUTHORIZATION, grantedContext);
            extensionEngine.execute(ExtensionPoint.AFTER_CONSENT_GRANTED, grantedContext);
        }
        return toResult(persisted);
    }

    @Override
    @Transactional
    public ContentRevisionResult createRevision(CreateConsentRevisionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        CommandContext context = requireContext(command.context());
        Consent consent = load(context.tenantId(), command.consentId());
        extensionEngine.execute(ExtensionPoint.BEFORE_CONTENT_REVISION,
                extensionContext(consent.id(), consent.consentType(), consent.clientId(), consent.acquisitionChannel(), consent.captureMethod(), null,
                        context, Map.of("currentRevisionNo", consent.currentRevisionNo())));
        ContentRevisionResult revision = contentApi.createNextFinalized(
                context.tenantId(), consent.id(), command.content(), context.actorId(), clock.instant());
        consent.linkContentRevision(revision.id(), revision.revisionNo(), context.actorId(), clock.instant());
        Consent persisted = persistChanges(consent, context);
        appendAudit("CREATE_CONTENT_REVISION", persisted, context, Map.of(
                "revisionNo", revision.revisionNo(), "contentHash", revision.contentHash()));
        extensionEngine.execute(ExtensionPoint.AFTER_CONTENT_REVISION,
                extensionContext(consent.id(), consent.consentType(), consent.clientId(), consent.acquisitionChannel(), consent.captureMethod(), null,
                        context, Map.of("revisionNo", revision.revisionNo())));
        return revision;
    }

    @Override
    @Transactional
    public ConsentResult requestAuthorization(RequestAuthorizationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        CommandContext context = requireContext(command.context());
        Consent consent = load(context.tenantId(), command.consentId());
        consent.requestAuthorization(command.reasonCode(), context.actorId(), clock.instant());
        Consent persisted = persistChanges(consent, context);
        appendAudit("REQUEST_AUTHORIZATION", persisted, context, Map.of());
        return toResult(persisted);
    }

    @Override
    @Transactional
    public ConsentResult bindSubject(BindConsentSubjectCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        CommandContext context = requireContext(command.context());
        Consent consent = load(context.tenantId(), command.consentId());

        if (!consent.bindSubject(command.subjectRef(), context.actorId(), clock.instant())) {
            return toResult(consent);
        }

        Consent persisted = persistChanges(consent, context);
        appendAudit("BIND_CONSENT_SUBJECT", persisted, context, Map.of("bindingPoint", "PREPARE"));
        return toResult(persisted);
    }

    @Override
    @Transactional
    public ConsentResult authorize(AuthorizeConsentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        CommandContext context = requireContext(command.context());
        Consent consent = load(context.tenantId(), command.consentId());
        consent.assertCanAuthorize();

        ExtensionContext grantContext = extensionContext(
                consent.id(), consent.consentType(), consent.clientId(), consent.acquisitionChannel(), consent.captureMethod(), null,
                context, Map.of("trustedSource", false));

        extensionEngine.execute(ExtensionPoint.BEFORE_CONSENT_GRANT, grantContext);
        extensionEngine.execute(ExtensionPoint.BEFORE_CONSENT_AUTHORIZATION, grantContext);
        Instant now = clock.instant();

        consent.bindSubject(command.subjectRef(), context.actorId(), now);

        UUID decisionId = captureDecision(consent, "AUTHORIZED", command.authorizationReference(), context,
                command.authenticationContext(), command.interactionProvenance(), command.decidedAt(), now);

        consent.recordDecision(decisionId, consent.currentRevisionId(), "AUTHORIZED", context.actorId(), now);
        consent.authorize(command.authorizationReference(), context.actorId(), now);
        Consent persisted = persistChanges(consent, context);

        appendAudit("AUTHORIZE_CONSENT", persisted, context,
                flowDecisionAuditDetails(command, decisionId, true));

        ExtensionContext grantedContext = extensionContext(
                consent.id(), consent.consentType(), consent.clientId(), consent.acquisitionChannel(), consent.captureMethod(), null,
                context, Map.of("decisionId", decisionId.toString()));

        extensionEngine.execute(ExtensionPoint.AFTER_CONSENT_AUTHORIZATION, grantedContext);
        extensionEngine.execute(ExtensionPoint.AFTER_CONSENT_GRANTED, grantedContext);
        return toResult(persisted);
    }

    @Override
    @Transactional
    public ConsentResult reject(RejectConsentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        CommandContext context = requireContext(command.context());
        Consent consent = load(context.tenantId(), command.consentId());
        consent.assertCanReject();
        Instant now = clock.instant();

        consent.bindSubject(command.subjectRef(), context.actorId(), now);

        UUID decisionId = captureDecision(consent, "REJECTED", null, context,
                command.authenticationContext(), command.interactionProvenance(), command.decidedAt(), now);

        consent.recordDecision(decisionId, consent.currentRevisionId(), "REJECTED", context.actorId(), now);
        consent.reject(command.reasonCode(), command.reasonDetail(), context.actorId(), now);
        Consent persisted = persistChanges(consent, context);

        appendAudit("REJECT_CONSENT", persisted, context,
                merge(reasonDetails(command.reasonCode(), command.reasonDetail()),
                        flowDecisionAuditDetails(command, decisionId, false)));
        return toResult(persisted);
    }

    @Override
    @Transactional
    public ConsentResult linkEvidenceBundle(LinkEvidenceBundleCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        CommandContext context = requireContext(command.context());
        Consent consent = load(context.tenantId(), command.consentId());
        consent.linkEvidenceBundle(command.bundleId(), command.revisionId(), command.evidenceStatus(),
                context.actorId(), clock.instant());
        Consent persisted = persistChanges(consent, context);
        appendAudit("LINK_EVIDENCE_BUNDLE", persisted, context,
                Map.of("bundleId", command.bundleId().toString(),
                        "evidenceStatus", command.evidenceStatus().name()));
        return toResult(persisted);
    }

    @Override
    @Transactional
    public ConsentResult updateEvidenceStatus(UpdateEvidenceStatusCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        CommandContext context = requireContext(command.context());
        Consent consent = load(context.tenantId(), command.consentId());
        consent.updateEvidenceStatus(command.bundleId(), command.evidenceStatus(),
                context.actorId(), clock.instant());
        Consent persisted = persistChanges(consent, context);
        appendAudit("UPDATE_EVIDENCE_STATUS", persisted, context,
                Map.of("bundleId", command.bundleId().toString(),
                        "evidenceStatus", command.evidenceStatus().name()));
        return toResult(persisted);
    }

    @Override
    @Transactional
    public ConsentResult suspend(SuspendConsentCommand command) {
        CommandContext context = requireContext(command.context());
        Consent consent = load(context.tenantId(), command.consentId());
        consent.suspend(command.reasonCode(), command.reasonDetail(), context.actorId(), clock.instant());
        Consent persisted = persistChanges(consent, context);
        appendAudit("SUSPEND_CONSENT", persisted, context,
                reasonDetails(command.reasonCode(), command.reasonDetail()));
        return toResult(persisted);
    }

    @Override
    @Transactional
    public ConsentResult revoke(RevokeConsentCommand command) {
        CommandContext context = requireContext(command.context());
        Consent consent = load(context.tenantId(), command.consentId());
        consent.revoke(command.reasonCode(), command.reasonDetail(), context.actorId(), clock.instant());
        Consent persisted = persistChanges(consent, context);
        appendAudit("REVOKE_CONSENT", persisted, context,
                reasonDetails(command.reasonCode(), command.reasonDetail()));
        return toResult(persisted);
    }

    @Override
    @Transactional
    public ConsentResult expire(ExpireConsentCommand command) {
        CommandContext context = requireContext(command.context());
        Consent consent = load(context.tenantId(), command.consentId());
        consent.expire(context.actorId(), clock.instant());
        Consent persisted = persistChanges(consent, context);
        appendAudit("EXPIRE_CONSENT", persisted, context, Map.of());
        return toResult(persisted);
    }

    @Override
    @Transactional
    public ConsentResult cancel(CancelConsentCommand command) {
        CommandContext context = requireContext(command.context());
        Consent consent = load(context.tenantId(), command.consentId());
        consent.cancel(command.reasonCode(), command.reasonDetail(), context.actorId(), clock.instant());
        Consent persisted = persistChanges(consent, context);
        appendAudit("CANCEL_CONSENT", persisted, context,
                reasonDetails(command.reasonCode(), command.reasonDetail()));
        return toResult(persisted);
    }

    @Override
    @Transactional(readOnly = true)
    public ConsentResult getConsent(String tenantId, UUID consentId) {
        return toResult(load(requireText(tenantId, "tenantId"), consentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsentHistoryResult> getHistory(String tenantId, UUID consentId) {
        tenantId = requireText(tenantId, "tenantId");
        load(tenantId, consentId);
        return historyPort.findByConsentId(tenantId, consentId);
    }

    private UUID captureDecision(
            Consent consent, String outcome, String authorizationReference,
            CommandContext context, Instant now) {
        return captureDecision(consent, outcome, authorizationReference, context, null, null, now, now);
    }

    private UUID captureDecision(
            Consent consent, String outcome, String authorizationReference,
            CommandContext context,
            vn.com.fis.consentcore.registry.api.authz.VerifiedAuthenticationContext authentication,
            vn.com.fis.consentcore.registry.api.authz.InteractionProvenance interaction,
            Instant decidedAt,
            Instant recordedAt) {
        UUID id = UUID.randomUUID();
        java.util.HashMap<String, Object> authenticationContext = new java.util.HashMap<>();
        authenticationContext.put("sourceSystem", context.sourceSystem());
        if (authentication != null) {
            authenticationContext.put("issuer", authentication.issuer());
            authenticationContext.put("authenticationTime", authentication.authenticationTime().toString());
            if (authentication.acr() != null) authenticationContext.put("acr", authentication.acr());
            authenticationContext.put("amr", authentication.amr());
            authenticationContext.put("verificationProfile", authentication.verificationProfile());
            if (authentication.evidenceRef() != null) authenticationContext.put("evidenceRef", authentication.evidenceRef());
            if (authentication.evidenceHash() != null) authenticationContext.put("evidenceHash", authentication.evidenceHash());
        }
        if (interaction != null) {
            authenticationContext.put("sourceInteractionRef", interaction.sourceInteractionRef());
            if (interaction.authorizationServerRef() != null) {
                authenticationContext.put("authorizationServerRef", interaction.authorizationServerRef());
            }
            if (interaction.asTransactionRef() != null) authenticationContext.put("asTransactionRef", interaction.asTransactionRef());
        }
        Instant effectiveDecisionTime = decidedAt == null ? recordedAt : decidedAt;
        return decisionCapturePort.append(new DecisionCapturePort.DecisionCaptureRecord(
                id, consent.tenantId(), consent.id(), consent.currentRevisionId(), outcome,
                hasText(consent.subjectId()) ? consent.subjectId() : context.actorId(),
                context.actorId(), consent.captureMethod(), consent.captureLocation(),
                authorizationReference, Map.copyOf(authenticationContext), effectiveDecisionTime, recordedAt));
    }

    private Consent persistChanges(Consent consent, CommandContext context) {
        List<vn.com.fis.consentcore.registry.domain.model.ConsentTransition> transitions =
                consent.pendingTransitions();
        List<DomainEvent> events = consent.pendingEvents();
        Consent persisted = consentRepository.save(consent);
        historyPort.append(transitions, context);
        events.forEach(event -> outboxWriter.append(event, context));
        consent.clearPendingChanges();
        return persisted;
    }

    private Consent load(String tenantId, UUID consentId) {
        Objects.requireNonNull(consentId, "consentId must not be null");
        return consentRepository.findById(tenantId, consentId)
                .orElseThrow(() -> new ConsentNotFoundException(tenantId, consentId));
    }

    private void appendAudit(String action, Consent consent, CommandContext context, Map<String, Object> details) {
        auditWriter.append(new AuditRecord(
                UUID.randomUUID(), context.tenantId(), "Consent", consent.id(), action, "SUCCESS",
                context.actorId(), context.actorType(), context.sourceSystem(), context.correlationId(),
                context.requestId(), details, clock.instant()));
    }

    private static ExtensionContext extensionContext(
            UUID consentId,
            String consentType,
            String clientId,
            vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel channel,
            vn.com.fis.consentcore.registry.domain.model.CaptureMethod captureMethod,
            String evidenceType,
            CommandContext context,
            Map<String, Object> attributes) {
        java.util.HashMap<String, Object> values = new java.util.HashMap<>(attributes);
        values.put("extensionPoint", "APPLICATION");
        return new ExtensionContext(context.tenantId(), consentId, consentType, clientId, channel,
                captureMethod, evidenceType, context, values, Map.of());
    }


    private static boolean isExternalAcquisition(
            vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel channel) {
        return channel == vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel.BRANCH
                || channel == vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel.CALL_CENTER
                || channel == vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel.PARTNER
                || channel == vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel.LEGACY_IMPORT;
    }

    private static Map<String, Object> flowDecisionAuditDetails(
            AuthorizeConsentCommand command, UUID decisionId, boolean authorizationReferencePresent) {
        java.util.HashMap<String, Object> details = new java.util.HashMap<>();
        details.put("decisionId", decisionId.toString());
        details.put("authorizationReferencePresent", authorizationReferencePresent);
        addFlowDecisionAudit(details, command.expectedRevisionNo(), command.expectedContentHash(),
                command.authenticationContext(), command.interactionProvenance());
        return Map.copyOf(details);
    }

    private static Map<String, Object> flowDecisionAuditDetails(
            RejectConsentCommand command, UUID decisionId, boolean authorizationReferencePresent) {
        java.util.HashMap<String, Object> details = new java.util.HashMap<>();
        details.put("decisionId", decisionId.toString());
        details.put("authorizationReferencePresent", authorizationReferencePresent);
        addFlowDecisionAudit(details, command.expectedRevisionNo(), command.expectedContentHash(),
                command.authenticationContext(), command.interactionProvenance());
        return Map.copyOf(details);
    }

    private static void addFlowDecisionAudit(
            java.util.HashMap<String, Object> details,
            Integer expectedRevisionNo,
            String expectedContentHash,
            vn.com.fis.consentcore.registry.api.authz.VerifiedAuthenticationContext authentication,
            vn.com.fis.consentcore.registry.api.authz.InteractionProvenance interaction) {
        if (expectedRevisionNo != null) details.put("expectedRevisionNo", expectedRevisionNo);
        if (expectedContentHash != null) details.put("contentHash", expectedContentHash);
        if (authentication != null) {
            details.put("authenticationIssuer", authentication.issuer());
            details.put("verificationProfile", authentication.verificationProfile());
        }
        if (interaction != null) {
            details.put("sourceInteractionRef", interaction.sourceInteractionRef());
            if (interaction.authorizationServerRef() != null) {
                details.put("authorizationServerRef", interaction.authorizationServerRef());
            }
        }
    }

    private static Map<String, Object> reasonDetails(String reasonCode, String reasonDetail) {
        return hasText(reasonDetail)
                ? Map.of("reasonCode", reasonCode, "reasonDetail", reasonDetail)
                : Map.of("reasonCode", reasonCode);
    }

    private static Map<String, Object> merge(Map<String, Object> first, Map<String, Object> second) {
        java.util.HashMap<String, Object> result = new java.util.HashMap<>(first);
        result.putAll(second);
        return Map.copyOf(result);
    }

    public static ConsentResult toResult(Consent consent) {
        return new ConsentResult(
                consent.id(), consent.tenantId(), consent.externalRequestId(), consent.consentType(),
                consent.subjectId(), consent.clientId(), consent.purpose(), consent.status(),
                consent.validFrom(), consent.validUntil(), consent.acquisitionChannel(), consent.captureMethod(),
                consent.sourceSystem(), consent.externalConsentId(), consent.evidencePolicy(),
                consent.evidenceStatus(), consent.trustedSourceAuthorization(), consent.authorizationReference(),
                consent.capturedAt(), consent.capturedBy(), consent.captureLocation(), consent.importBatchReference(),
                consent.currentRevisionId(), consent.currentRevisionNo(), consent.decisionId(),
                consent.evidenceBundleId(), consent.createdAt(), consent.updatedAt(), consent.createdBy(),
                consent.updatedBy(), consent.version());
    }

    private static CommandContext requireContext(CommandContext context) {
        return Objects.requireNonNull(context, "command context must not be null");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
