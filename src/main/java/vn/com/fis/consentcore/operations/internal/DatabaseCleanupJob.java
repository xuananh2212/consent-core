package vn.com.fis.consentcore.operations.internal;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Component
@ConditionalOnProperty(name = "consent.maintenance.cleanup-enabled", havingValue = "true", matchIfMissing = true)
class DatabaseCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(DatabaseCleanupJob.class);
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int outboxRetentionDays;

    DatabaseCleanupJob(
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${consent.maintenance.published-outbox-retention-days:14}") int outboxRetentionDays) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.outboxRetentionDays = Math.max(1, outboxRetentionDays);
    }

//    @Scheduled(cron = "${consent.maintenance.cleanup-cron:0 15 2 * * *}")
//    @Transactional
//    void cleanup() {
//        int idempotency = jdbc.update("delete from consent_idempotency where expires_at < ?", toTimestamp(clock.instant()));
//        int outbox = jdbc.update("""
//                delete from outbox_event
//                 where status = 'PUBLISHED'
//                   and published_at < ?
//                """, toTimestamp(clock.instant().minus(outboxRetentionDays, ChronoUnit.DAYS)));
//        int expiredUploadSessions = jdbc.update("""
//                update evidence_upload_session
//                   set status = 'EXPIRED'
//                 where status in ('CREATED', 'UPLOADING') and expires_at < ?
//                """, toTimestamp(clock.instant()));
//        if (idempotency > 0 || outbox > 0 || expiredUploadSessions > 0) {
//            log.info("Maintenance cleanup removed idempotency={} publishedOutbox={} expiredUploadSessions={}",
//                    idempotency, outbox, expiredUploadSessions);
//        }
//    }
}
