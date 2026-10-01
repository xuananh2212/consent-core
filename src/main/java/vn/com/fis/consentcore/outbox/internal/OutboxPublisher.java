package vn.com.fis.consentcore.outbox.internal;

import vn.com.fis.consentcore.outbox.api.OutboxMessage;
import vn.com.fis.consentcore.outbox.api.OutboxTransport;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcOutboxRepository repository;
    private final OutboxTransport transport;
    private final boolean enabled;
    private final int batchSize;
    private final int maxAttempts;
    private final String workerId = UUID.randomUUID().toString();

    OutboxPublisher(
            JdbcOutboxRepository repository,
            OutboxTransport transport,
            @Value("${consent.outbox.enabled:true}") boolean enabled,
            @Value("${consent.outbox.batch-size:50}") int batchSize,
            @Value("${consent.outbox.max-attempts:10}") int maxAttempts
    ) {
        this.repository = repository;
        this.transport = transport;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

//    @Scheduled(fixedDelayString = "${consent.outbox.fixed-delay-ms:1000}")
//    void publishPendingEvents() {
//        if (!enabled) return;
//        for (OutboxMessage message : repository.claimBatch(workerId, batchSize)) {
//            try {
//                transport.publish(message);
//                repository.markPublished(message.id());
//            } catch (RuntimeException ex) {
//                log.warn("Outbox publish failed id={} type={} attempt={}",
//                        message.id(), message.eventType(), message.attempts() + 1, ex);
//                repository.markFailed(message.id(), message.attempts(), maxAttempts, ex.getMessage());
//            }
//        }
//    }
}
