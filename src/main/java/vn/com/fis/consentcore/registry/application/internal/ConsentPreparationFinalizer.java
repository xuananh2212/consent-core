package vn.com.fis.consentcore.registry.application.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.fis.consentcore.content.api.ConsentContentApi;
import vn.com.fis.consentcore.content.api.ContentRevisionResult;
import vn.com.fis.consentcore.registry.api.command.BindConsentSubjectCommand;
import vn.com.fis.consentcore.registry.api.command.CreateConsentRevisionCommand;
import vn.com.fis.consentcore.registry.api.command.PrepareAuthorizationCommand;
import vn.com.fis.consentcore.registry.api.command.RequestAuthorizationCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;
import vn.com.fis.consentcore.registry.api.usecase.BindConsentSubjectUseCase;
import vn.com.fis.consentcore.registry.api.usecase.CreateConsentRevisionUseCase;
import vn.com.fis.consentcore.registry.api.usecase.GetConsentUseCase;
import vn.com.fis.consentcore.registry.api.usecase.RequestAuthorizationUseCase;
import vn.com.fis.consentcore.registry.application.internal.exception.AuthorizationBindingException;
import vn.com.fis.consentcore.registry.application.internal.exception.IdempotencyKeyReuseException;
import vn.com.fis.consentcore.registry.application.internal.exception.PreparationStaleException;
import vn.com.fis.consentcore.registry.application.internal.port.IdempotencyPort;
import vn.com.fis.consentcore.registry.application.internal.port.IdempotencyRecord;
import vn.com.fis.consentcore.registry.domain.model.ConsentStatus;

@Service
public class ConsentPreparationFinalizer {
    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofHours(24);

    private final GetConsentUseCase getConsentUseCase;
    private final CreateConsentRevisionUseCase createRevisionUseCase;
    private final RequestAuthorizationUseCase requestAuthorizationUseCase;
    private final BindConsentSubjectUseCase bindSubjectUseCase;
    private final ConsentContentApi contentApi;
    private final IdempotencyPort idempotencyPort;
    private final Clock clock;

    ConsentPreparationFinalizer(
            GetConsentUseCase getConsentUseCase,
            CreateConsentRevisionUseCase createRevisionUseCase,
            RequestAuthorizationUseCase requestAuthorizationUseCase,
            BindConsentSubjectUseCase bindSubjectUseCase,
            ConsentContentApi contentApi,
            IdempotencyPort idempotencyPort,
            Clock clock) {
        this.getConsentUseCase = getConsentUseCase;
        this.createRevisionUseCase = createRevisionUseCase;
        this.requestAuthorizationUseCase = requestAuthorizationUseCase;
        this.bindSubjectUseCase = bindSubjectUseCase;
        this.contentApi = contentApi;
        this.idempotencyPort = idempotencyPort;
        this.clock = clock;
    }

    @Transactional
    ConsentResult finalizePreparation(
            PrepareAuthorizationCommand command,
            String requestHash,
            long baseConsentVersion,
            Integer baseRevisionNo,
            String baseContentHash,
            ConsentPreparationMaterializer.MaterializationResult materialization) {
        String key = requireText(command.idempotencyKey(), "idempotencyKey");
        String tenantId = command.context().tenantId();

        idempotencyPort.acquireTransactionLock(tenantId, key, FlowManagerConsentService.OPERATION_PREPARE);

        Optional<IdempotencyRecord> prior = idempotencyPort.find(tenantId, key, FlowManagerConsentService.OPERATION_PREPARE);

        if (prior.isPresent()) {
            if (!prior.get().requestHash().equals(requestHash)) throw new IdempotencyKeyReuseException(key);
            return getConsentUseCase.getConsent(tenantId, prior.get().consentId());
        }

        ConsentResult current = getConsentUseCase.getConsent(tenantId, command.consentId());

        if (!Objects.equals(current.clientId(), command.clientId())) {
            throw new AuthorizationBindingException("clientId does not match consent requesting client");
        }

        if (current.status() == ConsentStatus.AWAITING_AUTHORIZATION) {
            ConsentResult bound = bindSubject(command, current);
            saveIdempotency(command, requestHash, current.id());
            return current;
        }

        if (current.status() != ConsentStatus.REGISTERED) {
            throw new AuthorizationBindingException("consent is not eligible for authorization preparation: " + current.status());
        }

        ContentRevisionResult currentRevision = contentApi.getCurrent(tenantId, current.id());

        if (current.version() != baseConsentVersion
                || !Objects.equals(currentRevision.revisionNo(), baseRevisionNo)
                || !Objects.equals(currentRevision.contentHash(), baseContentHash)) {
            throw new PreparationStaleException("Consent changed while backend data was being resolved; prepare again",
                    Map.of("consentId", current.id().toString()));
        }

        if (materialization.changed()) {
            createRevisionUseCase.createRevision(new CreateConsentRevisionCommand(
                    current.id(), materialization.content(), command.context()));
        }

        bindSubject(command, current);
        ConsentResult prepared = requestAuthorizationUseCase.requestAuthorization(new RequestAuthorizationCommand(
                current.id(), "FLOW_MANAGER_PREPARE", command.context()));

        saveIdempotency(command, requestHash, prepared.id());
        return prepared;
    }

    private ConsentResult bindSubject(PrepareAuthorizationCommand command, ConsentResult current) {
        if (command.subjectRef() == null || command.subjectRef().isBlank()) {
            return current; // APP_TO_APP_REDIRECT: bound later, at authorize/reject
        }
        return bindSubjectUseCase.bindSubject(new BindConsentSubjectCommand(
                current.id(), command.subjectRef(), command.context()));
    }

    private void saveIdempotency(PrepareAuthorizationCommand command, String requestHash, UUID consentId) {
        Instant now = clock.instant();
        idempotencyPort.save(new IdempotencyRecord(UUID.randomUUID(), command.context().tenantId(),
                command.idempotencyKey(), FlowManagerConsentService.OPERATION_PREPARE, requestHash,
                consentId, now, now.plus(IDEMPOTENCY_RETENTION)));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
