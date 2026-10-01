package vn.com.fis.consentcore.registry.application.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.content.api.ConsentContentApi;
import vn.com.fis.consentcore.content.api.ContentRevisionResult;
import vn.com.fis.consentcore.enrichment.api.ConsentDataResolutionRequest;
import vn.com.fis.consentcore.enrichment.api.ConsentDataResolver;
import vn.com.fis.consentcore.enrichment.api.DataPurpose;
import vn.com.fis.consentcore.enrichment.api.ResolutionPhase;
import vn.com.fis.consentcore.enrichment.api.ResolvedDataContext;
import vn.com.fis.consentcore.enrichment.api.ResolvedRequirementData;
import vn.com.fis.consentcore.policy.api.AuthorizationAssurancePolicyApi;
import vn.com.fis.consentcore.policy.api.PreparationEligibilityPolicyApi;
import vn.com.fis.consentcore.policy.api.RequiredAssurance;
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
import vn.com.fis.consentcore.registry.api.usecase.AuthorizeConsentUseCase;
import vn.com.fis.consentcore.registry.api.usecase.GetConsentAuthorizationContextUseCase;
import vn.com.fis.consentcore.registry.api.usecase.GetConsentCommandResultUseCase;
import vn.com.fis.consentcore.registry.api.usecase.GetConsentUseCase;
import vn.com.fis.consentcore.registry.api.usecase.PrepareConsentAuthorizationUseCase;
import vn.com.fis.consentcore.registry.api.usecase.RejectConsentFromFlowUseCase;
import vn.com.fis.consentcore.registry.api.usecase.RejectConsentUseCase;
import vn.com.fis.consentcore.registry.api.usecase.ValidateConsentAuthorizationUseCase;
import vn.com.fis.consentcore.registry.application.internal.exception.AuthorizationBindingException;
import vn.com.fis.consentcore.registry.application.internal.exception.IdempotencyKeyReuseException;
import vn.com.fis.consentcore.registry.application.internal.exception.PreparationStaleException;
import vn.com.fis.consentcore.registry.application.internal.port.AuthenticationEvidencePort;
import vn.com.fis.consentcore.registry.application.internal.port.IdempotencyPort;
import vn.com.fis.consentcore.registry.application.internal.port.IdempotencyRecord;
import vn.com.fis.consentcore.registry.domain.model.ConsentStatus;
import vn.com.fis.consentcore.shared.api.CommandContext;

@Service
public class FlowManagerConsentService implements
        GetConsentAuthorizationContextUseCase,
        PrepareConsentAuthorizationUseCase,
        AuthorizeConsentFromFlowUseCase,
        RejectConsentFromFlowUseCase,
        ValidateConsentAuthorizationUseCase,
        GetConsentCommandResultUseCase {

    public static final String OPERATION_PREPARE = "PREPARE_AUTHORIZATION";
    public static final String OPERATION_AUTHORIZE = "AUTHORIZE_CONSENT_FLOW";
    public static final String OPERATION_REJECT = "REJECT_CONSENT_FLOW";
    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofHours(24);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final GetConsentUseCase getConsentUseCase;
    private final ConsentContentApi contentApi;
    private final AuthorizeConsentUseCase authorizeConsentUseCase;
    private final RejectConsentUseCase rejectConsentUseCase;
    private final IdempotencyPort idempotencyPort;
    private final AuthenticationEvidencePort authenticationEvidencePort;
    private final AuthorizationAssurancePolicyApi assurancePolicyApi;
    private final PreparationEligibilityPolicyApi preparationEligibilityPolicyApi;
    private final FlowAuthorizationRequestHasher requestHasher;
    private final ConsentDataResolver dataResolver;
    private final ConsentPreparationMaterializer preparationMaterializer;
    private final ConsentPreparationFinalizer preparationFinalizer;
    private final Clock clock;

    public FlowManagerConsentService(
            GetConsentUseCase getConsentUseCase,
            ConsentContentApi contentApi,
            AuthorizeConsentUseCase authorizeConsentUseCase,
            RejectConsentUseCase rejectConsentUseCase,
            IdempotencyPort idempotencyPort,
            AuthenticationEvidencePort authenticationEvidencePort,
            AuthorizationAssurancePolicyApi assurancePolicyApi,
            PreparationEligibilityPolicyApi preparationEligibilityPolicyApi,
            FlowAuthorizationRequestHasher requestHasher,
            ConsentDataResolver dataResolver,
            ConsentPreparationMaterializer preparationMaterializer,
            ConsentPreparationFinalizer preparationFinalizer,
            Clock clock) {
        this.getConsentUseCase = getConsentUseCase;
        this.contentApi = contentApi;
        this.authorizeConsentUseCase = authorizeConsentUseCase;
        this.rejectConsentUseCase = rejectConsentUseCase;
        this.idempotencyPort = idempotencyPort;
        this.authenticationEvidencePort = authenticationEvidencePort;
        this.assurancePolicyApi = assurancePolicyApi;
        this.preparationEligibilityPolicyApi = preparationEligibilityPolicyApi;
        this.requestHasher = requestHasher;
        this.dataResolver = dataResolver;
        this.preparationMaterializer = preparationMaterializer;
        this.preparationFinalizer = preparationFinalizer;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ConsentAuthorizationContext getAuthorizationContext(String tenantId, UUID consentId) {
        return buildContext(requireText(tenantId, "tenantId"), Objects.requireNonNull(consentId));
    }

    @Override
    public ConsentPreparationResult prepareAuthorization(PrepareAuthorizationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        CommandContext context = Objects.requireNonNull(command.context(), "command context must not be null");
        String key = requireText(command.idempotencyKey(), "idempotencyKey");
        String clientId = requireText(command.clientId(), "clientId");
        String hash = requestHasher.hash(command);

        Optional<IdempotencyRecord> prior = idempotencyPort.find(context.tenantId(), key, OPERATION_PREPARE);
        if (prior.isPresent()) {
            verifyHash(key, hash, prior.get());
            return preparedResult(buildContext(context.tenantId(), prior.get().consentId()));
        }

        ConsentResult consent = getConsentUseCase.getConsent(context.tenantId(), command.consentId());
        requireClient(consent, clientId);
        String subjectRef = effectiveSubjectRef(command.subjectRef(), consent.subjectId());

        if (command.subjectRef() != null && !command.subjectRef().isBlank()
                && consent.subjectId() != null && !Objects.equals(consent.subjectId(), command.subjectRef().trim())) {
            throw new AuthorizationBindingException("subjectRef does not match consent subject");
        }

        requireSubjectRule(command.interactionMode(), command.subjectRef());
        ContentRevisionResult baseRevision = contentApi.getCurrent(context.tenantId(), consent.id());

        if (consent.status() == ConsentStatus.AWAITING_AUTHORIZATION) {
            ConsentPreparationMaterializer.MaterializationResult unchanged =
                    new ConsentPreparationMaterializer.MaterializationResult(toContentInput(baseRevision), false);
            preparationFinalizer.finalizePreparation(command, hash, consent.version(), baseRevision.revisionNo(),
                    baseRevision.contentHash(), unchanged);
            return preparedResult(buildContext(context.tenantId(), consent.id()));
        }

        if (consent.status() != ConsentStatus.REGISTERED) {
            throw new AuthorizationBindingException("consent is not eligible for authorization preparation: " + consent.status());
        }

        Map<String,Object> parameters = new java.util.LinkedHashMap<>();
        parameters.put("consentPurpose", consent.purpose());
        parameters.put("acquisitionChannel", consent.acquisitionChannel() == null ? null : consent.acquisitionChannel().name());
        parameters.put("effectiveScopes", command.scopes());
        parameters.values().removeIf(Objects::isNull);

        // v0.5 post-authentication gate: resolve POLICY facts first. Flow Manager only triggers prepare;
        // Core owns which checks apply and the ALLOW/DENY business conclusion.
        ResolvedDataContext policyData = dataResolver.resolve(new ConsentDataResolutionRequest(
                consent.id(), consent.consentType(), ResolutionPhase.PREPARE_AUTHORIZATION,
                subjectRef, clientId, command.scopes(), Set.of(DataPurpose.POLICY), parameters, context));
        if (!policyData.requirements().isEmpty()) {
            var eligibility = preparationEligibilityPolicyApi.evaluate(
                    new PreparationEligibilityPolicyApi.PreparationEligibilityRequest(
                            consent.id(), consent.consentType(), clientId, subjectRef, command.scopes(),
                            policyData.requirements(), context));
            if (!eligibility.allowed()) {
                return ConsentPreparationResult.denied(
                        buildContext(context.tenantId(), consent.id()),
                        eligibility.reasonCode(), eligibility.messageKey(), eligibility.retryable(),
                        context.correlationId());
            }
        }

        // Only after eligibility ALLOW (or no POLICY gate) do we spend work on presentation/context/selection data.
        ResolvedDataContext resolved = dataResolver.resolve(new ConsentDataResolutionRequest(
                consent.id(), consent.consentType(), ResolutionPhase.PREPARE_AUTHORIZATION,
                subjectRef, clientId, command.scopes(), Set.of(DataPurpose.CONTEXT, DataPurpose.SELECTION),
                parameters, context));
        List<ConsentPreparationResult.SelectionRequirement> selectionRequirements = selectionRequirements(resolved);

        if (resolved.hasSelectionRequirements()) {
            boolean selectionMissing = resolved.selectionRequirements().stream()
                    .anyMatch(item -> !command.selections().containsKey(item.requirementCode()));
            if (selectionMissing) {
                return new ConsentPreparationResult(ConsentPreparationResult.SELECTION_REQUIRED,
                        buildContext(context.tenantId(), consent.id()), resolved.selectionContextHash(), selectionRequirements);
            }
            if (command.selectionContextHash() == null || command.selectionContextHash().isBlank()) {
                throw new PreparationStaleException("selectionContextHash is required when submitting PSU selections",
                        Map.of("consentId", consent.id().toString()));
            }
            if (!Objects.equals(resolved.selectionContextHash(), command.selectionContextHash().trim())) {
                throw new PreparationStaleException("Backend candidate data changed after it was presented; re-present selection",
                        Map.of("consentId", consent.id().toString(), "currentSelectionContextHash", resolved.selectionContextHash()));
            }
        }

        ConsentPreparationMaterializer.MaterializationResult materialization =
                preparationMaterializer.materialize(baseRevision, resolved, command.selections());
        preparationFinalizer.finalizePreparation(command, hash, consent.version(), baseRevision.revisionNo(),
                baseRevision.contentHash(), materialization);
        return new ConsentPreparationResult(ConsentPreparationResult.PREPARED,
                buildContext(context.tenantId(), consent.id()), null, List.of());
    }

    @Override
    @Transactional
    public ConsentResult authorizeFromFlow(AuthorizeConsentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireFlowBound(command.idempotencyKey(), command.expectedVersion(), command.expectedRevisionNo(),
                command.expectedContentHash(), command.clientId(), command.subjectRef(),
                command.authenticationContext(), command.interactionProvenance(), command.context());
        String key = requireText(command.idempotencyKey(), "idempotencyKey");
        String hash = requestHasher.hash(command);
        CommandContext context = command.context();
        idempotencyPort.acquireTransactionLock(context.tenantId(), key, OPERATION_AUTHORIZE);
        Optional<IdempotencyRecord> prior = idempotencyPort.find(context.tenantId(), key, OPERATION_AUTHORIZE);
        if (prior.isPresent()) {
            verifyHash(key, hash, prior.get());
            return getConsentUseCase.getConsent(context.tenantId(), prior.get().consentId());
        }

        ConsentResult consent = validateDecisionBinding(command.consentId(), command.clientId(), command.subjectRef(),
                command.expectedVersion(), command.expectedRevisionNo(), command.expectedContentHash(),
                command.authenticationContext(), command.interactionProvenance(), command.decidedAt(), context, true);
        Instant now = clock.instant();
        ConsentResult persisted = authorizeConsentUseCase.authorize(command);
        appendAuthenticationEvidence(persisted, command.authenticationContext(), command.interactionProvenance(), now);
        idempotencyPort.save(new IdempotencyRecord(UUID.randomUUID(), context.tenantId(), key,
                OPERATION_AUTHORIZE, hash, persisted.id(), now, now.plus(IDEMPOTENCY_RETENTION)));
        return persisted;
    }

    @Override
    @Transactional
    public ConsentResult rejectFromFlow(RejectConsentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireFlowBound(command.idempotencyKey(), command.expectedVersion(), command.expectedRevisionNo(),
                command.expectedContentHash(), command.clientId(), command.subjectRef(),
                command.authenticationContext(), command.interactionProvenance(), command.context());
        String key = requireText(command.idempotencyKey(), "idempotencyKey");
        String hash = requestHasher.hash(command);
        CommandContext context = command.context();
        idempotencyPort.acquireTransactionLock(context.tenantId(), key, OPERATION_REJECT);
        Optional<IdempotencyRecord> prior = idempotencyPort.find(context.tenantId(), key, OPERATION_REJECT);
        if (prior.isPresent()) {
            verifyHash(key, hash, prior.get());
            return getConsentUseCase.getConsent(context.tenantId(), prior.get().consentId());
        }

        validateDecisionBinding(command.consentId(), command.clientId(), command.subjectRef(),
                command.expectedVersion(), command.expectedRevisionNo(), command.expectedContentHash(),
                command.authenticationContext(), command.interactionProvenance(), command.decidedAt(), context, false);
        Instant now = clock.instant();
        ConsentResult persisted = rejectConsentUseCase.reject(command);
        appendAuthenticationEvidence(persisted, command.authenticationContext(), command.interactionProvenance(), now);
        idempotencyPort.save(new IdempotencyRecord(UUID.randomUUID(), context.tenantId(), key,
                OPERATION_REJECT, hash, persisted.id(), now, now.plus(IDEMPOTENCY_RETENTION)));
        return persisted;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizationValidationResult validateAuthorization(String tenantId, AuthorizationValidationRequest request) {
        tenantId = requireText(tenantId, "tenantId");
        Objects.requireNonNull(request, "request must not be null");
        ConsentResult consent = getConsentUseCase.getConsent(tenantId, request.consentId());
        List<String> reasons = new ArrayList<>();
        ContentRevisionResult revision = null;
        try {
            revision = contentApi.getCurrent(tenantId, consent.id());
        } catch (RuntimeException ex) {
            reasons.add("PRESENTATION_NOT_READY");
        }

        Instant now = clock.instant();
        if (consent.status() != ConsentStatus.AUTHORIZED) reasons.add("CONSENT_NOT_AUTHORIZED");
        if (!Objects.equals(consent.clientId(), request.clientId())) reasons.add("CLIENT_MISMATCH");
        if (!Objects.equals(consent.subjectId(), request.subjectRef())) reasons.add("SUBJECT_MISMATCH");
        if (consent.validUntil() == null || !now.isBefore(consent.validUntil())) reasons.add("CONSENT_EXPIRED");
        if (revision == null || !Objects.equals(revision.revisionNo(), request.revisionNo())) reasons.add("REVISION_MISMATCH");
        if (revision == null || !Objects.equals(revision.contentHash(), request.contentHash())) reasons.add("CONTENT_HASH_MISMATCH");
        if (consent.decisionId() == null) reasons.add("DECISION_MISSING");

        Optional<AuthenticationEvidencePort.AuthenticationEvidenceRecord> authenticationEvidence = Optional.empty();
        if (consent.decisionId() != null && request.sourceInteractionRef() != null) {
            authenticationEvidence = authenticationEvidencePort.findMatching(tenantId, consent.id(), consent.decisionId(),
                    request.sourceInteractionRef(), request.authorizationServerRef(), request.asTransactionRef());
            if (authenticationEvidence.isEmpty()) reasons.add("INTERACTION_PROVENANCE_MISMATCH");
            else if (!Objects.equals(authenticationEvidence.get().subjectRef(), request.subjectRef())) {
                reasons.add("AUTHENTICATION_SUBJECT_MISMATCH");
            }
        } else {
            reasons.add("INTERACTION_PROVENANCE_MISSING");
        }
        if (authenticationEvidence.isPresent()) {
            RequiredAssurance assurance = assurancePolicyApi.resolve(tenantId, consent.consentType(), consent.clientId());
            collectAssuranceReasons(authenticationEvidence.get().authentication(), assurance, now, reasons);
        }

        return new AuthorizationValidationResult(reasons.isEmpty(), consent.id(), consent.status().name(),
                consent.decisionId(), consent.version(), revision == null ? null : revision.revisionNo(),
                revision == null ? null : revision.contentHash(), consent.subjectId(), consent.clientId(),
                authenticationEvidence.map(value -> value.authentication().issuer()).orElse(null), now, reasons);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CommandResult> getCommandResult(String tenantId, String operation, String idempotencyKey) {
        tenantId = requireText(tenantId, "tenantId");
        operation = requireOperation(operation);
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        String finalTenantId = tenantId;
        String finalOperation = operation;
        String finalKey = idempotencyKey;
        return idempotencyPort.find(finalTenantId, finalKey, finalOperation).map(record -> {
            ConsentResult consent = getConsentUseCase.getConsent(finalTenantId, record.consentId());
            return new CommandResult(record.operation(), record.idempotencyKey(), consent.id(),
                    consent.status().name(), consent.decisionId(), consent.version(), record.createdAt());
        });
    }

    private ConsentAuthorizationContext buildContext(String tenantId, UUID consentId) {
        ConsentResult consent = getConsentUseCase.getConsent(tenantId, consentId);
        ContentRevisionResult revision = null;
        if (consent.currentRevisionId() != null) revision = contentApi.getCurrent(tenantId, consentId);
        RequiredAssurance assurance = assurancePolicyApi.resolve(tenantId, consent.consentType(), consent.clientId());
        return new ConsentAuthorizationContext(consent.id(), consent.status().name(), consent.consentType(),
                consent.clientId(), consent.subjectId(), revision == null ? null : revision.revisionNo(),
                revision == null ? null : revision.contentHash(), consent.version(), consent.validUntil(),
                consent.evidencePolicy().name(), consent.evidenceStatus().name(), assurance,
                revision == null ? List.of() : revision.permissions().stream().map(p -> p.permissionCode()).distinct().toList(),
                revision == null ? List.of() : revision.resources().stream().map(r -> r.resourceType()).distinct().toList(),
                revision != null && "FINALIZED".equals(revision.status())
                        && consent.status() != ConsentStatus.REGISTERED);
    }

    /**
     * Subject rule of prepare-authorization per interaction mode:
     * BROWSER_REDIRECT / CIBA must bind the authenticated PSU now; APP_TO_APP_REDIRECT has no PSU yet
     */
    private static void requireSubjectRule(String interactionMode, String subjectRef) {
        boolean hasSubject = subjectRef != null && !subjectRef.isBlank();

        if (interactionMode == null || interactionMode.isBlank()) {
            return;
        }

        switch (interactionMode) {
            case "BROWSER_REDIRECT", "CIBA" -> {
                if (!hasSubject) {
                    throw new AuthorizationBindingException(
                            "subjectRef is required to prepare a " + interactionMode + " interaction");
                }
            }
            case "APP_TO_APP_REDIRECT" -> {
                if (hasSubject) {
                    throw new AuthorizationBindingException(
                            "subjectRef must not be sent before the PSU decides in APP_TO_APP_REDIRECT");
                }
            }
            default -> throw new AuthorizationBindingException("unsupported interactionMode " + interactionMode);
        }
    }

    private ConsentResult validateDecisionBinding(
            UUID consentId, String clientId, String subjectRef, Long expectedVersion,
            Integer expectedRevisionNo, String expectedContentHash,
            VerifiedAuthenticationContext authentication, InteractionProvenance interaction,
            Instant decidedAt, CommandContext context, boolean authorizing) {
        ConsentResult consent = getConsentUseCase.getConsent(context.tenantId(), consentId);

        if (consent.status() != ConsentStatus.AWAITING_AUTHORIZATION) {
            throw new AuthorizationBindingException("consent state must be AWAITING_AUTHORIZATION");
        }

        requireClient(consent, clientId);

        if (subjectRef == null || subjectRef.isBlank()) {
            throw new AuthorizationBindingException("subjectRef is required for a PSU decision");
        }

        if (consent.subjectId() != null && !consent.subjectId().equals(subjectRef)) {
            throw new AuthorizationBindingException("subjectRef does not match consent subject");
        }

        if (consent.version() != expectedVersion) {
            throw new AuthorizationBindingException("expectedVersion does not match current consent version");
        }

        ContentRevisionResult revision = contentApi.getCurrent(context.tenantId(), consent.id());

        if (!Objects.equals(revision.revisionNo(), expectedRevisionNo)) {
            throw new AuthorizationBindingException("expectedRevisionNo does not match current revision");
        }

        if (!Objects.equals(revision.contentHash(), expectedContentHash)) {
            throw new AuthorizationBindingException("expectedContentHash does not match current immutable revision");
        }

        Instant now = clock.instant();

        if (now.isBefore(consent.validFrom()) || !now.isBefore(consent.validUntil())) {
            throw new AuthorizationBindingException("consent validity window is not active");
        }

        if (!Objects.equals(authentication.subjectRef(), subjectRef)) {
            throw new AuthorizationBindingException("authentication subject does not match decision subject");
        }

        if (authentication.authenticationTime().isAfter(now.plus(CLOCK_SKEW))) {
            throw new AuthorizationBindingException("authenticationTime is in the future");
        }

        Instant effectiveDecisionTime = decidedAt == null ? now : decidedAt;

        if (effectiveDecisionTime.isAfter(now.plus(CLOCK_SKEW))) {
            throw new AuthorizationBindingException("decidedAt is in the future");
        }

        if (effectiveDecisionTime.isBefore(authentication.authenticationTime())) {
            throw new AuthorizationBindingException("decidedAt cannot be before authenticationTime");
        }

        if (!Objects.equals(interaction.sourceSystem(), context.sourceSystem())) {
            throw new AuthorizationBindingException("interaction sourceSystem does not match authenticated caller");
        }

        RequiredAssurance assurance = assurancePolicyApi.resolve(context.tenantId(), consent.consentType(), consent.clientId());
        validateAssurance(authentication, assurance, now);

        if (authorizing && "REQUIRED".equals(consent.evidencePolicy().name())
                && !"VERIFIED".equals(consent.evidenceStatus().name())) {
            throw new AuthorizationBindingException("required consent evidence is not verified");
        }

        return consent;
    }

    private static void validateAssurance(
            VerifiedAuthenticationContext authentication, RequiredAssurance assurance, Instant now) {
        if (assurance.authenticationRequired() && authentication == null) {
            throw new AuthorizationBindingException("verified authentication context is required");
        }
        if (authentication == null) return;
        if (assurance.requiredAcr() != null && !assurance.requiredAcr().equals(authentication.acr())) {
            throw new AuthorizationBindingException("authentication ACR does not satisfy policy");
        }
        Set<String> actualAmr = new HashSet<>(authentication.amr());
        if (!actualAmr.containsAll(assurance.requiredAmr())) {
            throw new AuthorizationBindingException("authentication AMR does not satisfy policy");
        }
        if (assurance.maxAuthenticationAgeSeconds() != null
                && Duration.between(authentication.authenticationTime(), now).getSeconds()
                > assurance.maxAuthenticationAgeSeconds()) {
            throw new AuthorizationBindingException("authentication is older than policy allows");
        }
        if (assurance.authenticationEvidenceRequired()
                && isBlank(authentication.evidenceRef()) && isBlank(authentication.evidenceHash())) {
            throw new AuthorizationBindingException("authentication evidence reference/hash is required by policy");
        }
    }

    private static void collectAssuranceReasons(
            VerifiedAuthenticationContext authentication, RequiredAssurance assurance,
            Instant now, List<String> reasons) {
        if (assurance.requiredAcr() != null && !assurance.requiredAcr().equals(authentication.acr())) {
            reasons.add("ACR_POLICY_NOT_SATISFIED");
        }
        if (!new HashSet<>(authentication.amr()).containsAll(assurance.requiredAmr())) {
            reasons.add("AMR_POLICY_NOT_SATISFIED");
        }
        if (assurance.maxAuthenticationAgeSeconds() != null
                && Duration.between(authentication.authenticationTime(), now).getSeconds()
                > assurance.maxAuthenticationAgeSeconds()) {
            reasons.add("AUTHENTICATION_TOO_OLD");
        }
        if (assurance.authenticationEvidenceRequired()
                && isBlank(authentication.evidenceRef()) && isBlank(authentication.evidenceHash())) {
            reasons.add("AUTHENTICATION_EVIDENCE_REQUIRED");
        }
    }

    private void appendAuthenticationEvidence(
            ConsentResult consent, VerifiedAuthenticationContext authentication,
            InteractionProvenance interaction, Instant now) {
        if (authentication == null || interaction == null || consent.decisionId() == null) return;
        authenticationEvidencePort.append(new AuthenticationEvidencePort.AuthenticationEvidenceRecord(
                UUID.randomUUID(), consent.tenantId(), consent.id(), consent.decisionId(),
                authentication.subjectRef(), interaction, authentication, now));
    }

    private static ConsentPreparationResult preparedResult(ConsentAuthorizationContext context) {
        return new ConsentPreparationResult(ConsentPreparationResult.PREPARED, context, null, List.of());
    }

    private static List<ConsentPreparationResult.SelectionRequirement> selectionRequirements(ResolvedDataContext resolved) {
        return resolved.selectionRequirements().stream().map(item -> new ConsentPreparationResult.SelectionRequirement(
                item.requirementCode(), item.dataType(), item.required(), item.candidates(),
                presentationConfiguration(item))).toList();
    }

    private static Map<String,Object> presentationConfiguration(ResolvedRequirementData item) {
        Object value = item.requirementConfiguration().get("presentation");
        if (!(value instanceof Map<?,?> map)) return Map.of();
        Map<String,Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key,itemValue) -> result.put(String.valueOf(key), itemValue));
        return Map.copyOf(result);
    }

    private static vn.com.fis.consentcore.content.api.ContentRevisionInput toContentInput(ContentRevisionResult revision) {
        return new vn.com.fis.consentcore.content.api.ContentRevisionInput(revision.purposeCode(), revision.permissions(),
                revision.resources(), revision.constraints(), revision.obligations(), revision.presentationSnapshot());
    }

    private static String effectiveSubjectRef(String requested, String persisted) {
        if (requested != null && !requested.isBlank()) return requested.trim();
        return persisted;
    }

    private static void requireFlowBound(
            String idempotencyKey, Long expectedVersion, Integer expectedRevisionNo,
            String expectedContentHash, String clientId, String subjectRef,
            VerifiedAuthenticationContext authentication, InteractionProvenance interaction,
            CommandContext context) {
        requireText(idempotencyKey, "idempotencyKey");
        if (expectedVersion == null || expectedVersion < 0) throw new AuthorizationBindingException("expectedVersion is required");
        if (expectedRevisionNo == null || expectedRevisionNo <= 0) throw new AuthorizationBindingException("expectedRevisionNo is required");
        requireText(expectedContentHash, "expectedContentHash");
        requireText(clientId, "clientId");
        requireText(subjectRef, "subjectRef");
        Objects.requireNonNull(authentication, "authenticationContext must not be null");
        Objects.requireNonNull(interaction, "interactionProvenance must not be null");
        Objects.requireNonNull(context, "command context must not be null");
    }

    private static void requireClient(ConsentResult consent, String clientId) {
        if (!Objects.equals(consent.clientId(), clientId)) {
            throw new AuthorizationBindingException("clientId does not match consent requesting client");
        }
    }

    private static void verifyHash(String key, String expectedHash, IdempotencyRecord prior) {
        if (!prior.requestHash().equals(expectedHash)) throw new IdempotencyKeyReuseException(key);
    }

    private static String requireOperation(String value) {
        value = requireText(value, "operation");
        if (!Set.of(OPERATION_PREPARE, OPERATION_AUTHORIZE, OPERATION_REJECT).contains(value)) {
            throw new IllegalArgumentException("Unsupported operation");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
