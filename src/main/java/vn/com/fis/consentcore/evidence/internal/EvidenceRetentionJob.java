package vn.com.fis.consentcore.evidence.internal;

import vn.com.fis.consentcore.evidence.api.EvidenceStoragePort;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Component
@ConditionalOnProperty(name = "consent.evidence.retention-job-enabled", havingValue = "true", matchIfMissing = true)
class EvidenceRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(EvidenceRetentionJob.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final EvidenceStoragePort storage;
    private final Clock clock;
    private final int batchSize;

    EvidenceRetentionJob(
            JdbcTemplate jdbc,
            TransactionTemplate transactionTemplate,
            EvidenceStoragePort storage,
            Clock clock,
            @Value("${consent.evidence.retention-batch-size:50}") int batchSize) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.storage = storage;
        this.clock = clock;
        this.batchSize = Math.max(1, batchSize);
    }

//    @Scheduled(cron = "${consent.evidence.retention-cron:0 45 2 * * *}")
//    void purgeExpiredBinaryObjects() {
//        List<Candidate> candidates = jdbc.query("""
//                select a.id artifact_id, a.object_key, b.id bundle_id, b.tenant_id, b.consent_id
//                  from consent_evidence_artifact a
//                  join consent_evidence_bundle b on b.id = a.bundle_id
//                  join consent c on c.id = b.consent_id and c.tenant_id = b.tenant_id
//                 where b.legal_hold = false and b.retention_until is not null
//                   and b.retention_until < current_timestamp
//                   and c.status in ('REJECTED', 'REVOKED', 'EXPIRED', 'CANCELLED')
//                   and a.status <> 'DELETED'
//                 order by b.retention_until, a.created_at
//                 limit ?
//                """, (rs, n) -> new Candidate(
//                        UuidUtils.fromBytes(rs.getBytes("artifact_id")), rs.getString("object_key"),
//                        UuidUtils.fromBytes(rs.getBytes("bundle_id")), rs.getString("tenant_id"),
//                        UuidUtils.fromBytes(rs.getBytes("consent_id"))), batchSize);
//        for (Candidate candidate : candidates) {
//            try {
//                storage.delete(candidate.objectKey());
//                transactionTemplate.executeWithoutResult(status -> markDeleted(candidate));
//            } catch (RuntimeException failure) {
//                log.warn("Cannot delete retained evidence object artifact={} key={}",
//                        candidate.artifactId(), candidate.objectKey(), failure);
//            }
//        }
//    }

    private void markDeleted(Candidate candidate) {
        int changed = jdbc.update("""
                update consent_evidence_artifact
                   set status = 'DELETED', deleted_at = ?, deleted_by = 'evidence-retention-job'
                 where id = ? and tenant_id = ? and status <> 'DELETED'
                """, toTimestamp(clock.instant()), UuidUtils.toBytes(candidate.artifactId()), candidate.tenantId());

        if (changed == 0) return;

        jdbc.update("""
                insert into evidence_access_log(
                    id, tenant_id, consent_id, bundle_id, artifact_id, action,
                    actor_id, actor_type, source_system, correlation_id, occurred_at)
                values (?, ?, ?, ?, ?, 'RETENTION_DELETE', 'evidence-retention-job',
                        'SYSTEM', 'CONSENT_CORE', ?, ?)
                """, UuidUtils.toBytes(UUID.randomUUID()), candidate.tenantId(), UuidUtils.toBytes(candidate.consentId()),
                UuidUtils.toBytes(candidate.bundleId()), UuidUtils.toBytes(candidate.artifactId()),
                "retention-" + candidate.bundleId(), toTimestamp(clock.instant()));

        Integer remaining = jdbc.queryForObject("""
                select count(*) from consent_evidence_artifact
                 where bundle_id = ? and tenant_id = ? and status <> 'DELETED'
                """, Integer.class, UuidUtils.toBytes(candidate.bundleId()), candidate.tenantId());

        if (remaining != null && remaining == 0) {
            jdbc.update("""
                    update consent_evidence_bundle
                       set status = 'EXPIRED', updated_at = ?, updated_by = 'evidence-retention-job',
                           version = version + 1
                     where id = ? and tenant_id = ? and legal_hold = 0
                    """, toTimestamp(clock.instant()), UuidUtils.toBytes(candidate.bundleId()), candidate.tenantId());
        }
    }

    private record Candidate(
            UUID artifactId, String objectKey, UUID bundleId, String tenantId, UUID consentId) { }
}
