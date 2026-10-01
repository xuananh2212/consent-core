package vn.com.fis.consentcore.registry.application.internal;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.com.fis.consentcore.registry.api.command.ExpireConsentCommand;
import vn.com.fis.consentcore.registry.api.usecase.ExpireConsentUseCase;
import vn.com.fis.consentcore.registry.application.internal.port.ConsentRepositoryPort;
import vn.com.fis.consentcore.shared.api.ActorType;
import vn.com.fis.consentcore.shared.api.CommandContext;

@Component
@ConditionalOnProperty(name = "consent.maintenance.expiry-enabled", havingValue = "true", matchIfMissing = true)
class ConsentExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(ConsentExpiryJob.class);
    private final ConsentRepositoryPort repository;
    private final ExpireConsentUseCase expireUseCase;
    private final Clock clock;
    private final int batchSize;

    ConsentExpiryJob(
            ConsentRepositoryPort repository,
            ExpireConsentUseCase expireUseCase,
            Clock clock,
            @Value("${consent.maintenance.expiry-batch-size:100}") int batchSize) {
        this.repository = repository;
        this.expireUseCase = expireUseCase;
        this.clock = clock;
        this.batchSize = Math.max(1, batchSize);
    }

//    @Scheduled(fixedDelayString = "${consent.maintenance.expiry-fixed-delay-ms:60000}")
//    void expireDueConsents() {
//        var candidates = repository.findExpiryCandidates(clock.instant(), batchSize);
//        for (var consent : candidates) {
//            String correlationId = "expiry-" + consent.id();
//            CommandContext context = new CommandContext(
//                    consent.tenantId(), "consent-expiry-job", ActorType.SYSTEM,
//                    correlationId, UUID.randomUUID().toString(), "CONSENT_CORE", clock.instant());
//            try {
//                expireUseCase.expire(new ExpireConsentCommand(consent.id(), context));
//            } catch (OptimisticLockingFailureException conflict) {
//                log.debug("Consent expiry was handled concurrently id={}", consent.id());
//            } catch (RuntimeException failure) {
//                log.warn("Cannot expire consent id={} tenant={}", consent.id(), consent.tenantId(), failure);
//            }
//        }
//    }
}
