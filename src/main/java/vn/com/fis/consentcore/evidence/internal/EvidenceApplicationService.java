package vn.com.fis.consentcore.evidence.internal;

import vn.com.fis.consentcore.evidence.api.EvidenceMalwareScanner;
import vn.com.fis.consentcore.evidence.api.EvidenceStoragePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.com.fis.consentcore.audit.api.AuditRecord;
import vn.com.fis.consentcore.audit.api.AuditWriter;
import vn.com.fis.consentcore.registry.api.command.LinkEvidenceBundleCommand;
import vn.com.fis.consentcore.registry.api.command.UpdateEvidenceStatusCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;
import vn.com.fis.consentcore.registry.api.usecase.GetConsentUseCase;
import vn.com.fis.consentcore.registry.api.usecase.LinkEvidenceBundleUseCase;
import vn.com.fis.consentcore.registry.api.usecase.UpdateEvidenceStatusUseCase;
import vn.com.fis.consentcore.registry.domain.model.EvidencePolicy;
import vn.com.fis.consentcore.registry.domain.model.EvidenceStatus;
import vn.com.fis.consentcore.evidence.api.EvidenceArtifactResult;
import vn.com.fis.consentcore.evidence.api.EvidenceBundleResult;
import vn.com.fis.consentcore.evidence.api.EvidenceDownload;
import vn.com.fis.consentcore.evidence.api.EvidenceManagementApi;
import vn.com.fis.consentcore.evidence.api.EvidenceVerificationResult;
import vn.com.fis.consentcore.evidence.api.EvidenceUploadSessionResult;
import vn.com.fis.consentcore.evidence.api.event.EvidenceArtifactAttachedEvent;
import vn.com.fis.consentcore.evidence.api.event.EvidenceArtifactSupersededEvent;
import vn.com.fis.consentcore.evidence.api.event.EvidenceVerificationCompletedEvent;
import vn.com.fis.consentcore.extension.api.ExtensionContext;
import vn.com.fis.consentcore.extension.api.ExtensionEngine;
import vn.com.fis.consentcore.extension.api.ExtensionPoint;
import vn.com.fis.consentcore.outbox.api.OutboxWriter;
import vn.com.fis.consentcore.shared.api.CommandContext;
import vn.com.fis.consentcore.shared.error.ErrorCode;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Service
public class EvidenceApplicationService implements EvidenceManagementApi {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final EvidenceStoragePort storage;
    private final EvidenceMalwareScanner malwareScanner;
    private final boolean verificationRequiresCleanScan;
    private final GetConsentUseCase getConsentUseCase;
    private final LinkEvidenceBundleUseCase linkBundleUseCase;
    private final UpdateEvidenceStatusUseCase updateStatusUseCase;
    private final ExtensionEngine extensionEngine;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    public EvidenceApplicationService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            EvidenceStoragePort storage,
            EvidenceMalwareScanner malwareScanner,
            @Value("${consent.evidence.verification-requires-clean-malware-scan:false}")
            boolean verificationRequiresCleanScan,
            GetConsentUseCase getConsentUseCase,
            LinkEvidenceBundleUseCase linkBundleUseCase,
            UpdateEvidenceStatusUseCase updateStatusUseCase,
            ExtensionEngine extensionEngine,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.storage = storage;
        this.malwareScanner = malwareScanner;
        this.verificationRequiresCleanScan = verificationRequiresCleanScan;
        this.getConsentUseCase = getConsentUseCase;
        this.linkBundleUseCase = linkBundleUseCase;
        this.updateStatusUseCase = updateStatusUseCase;
        this.extensionEngine = extensionEngine;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }


    @Override
    @Transactional
    public EvidenceUploadSessionResult createUploadSession(
            UUID consentId,
            String evidenceType,
            String originalFilename,
            String mediaType,
            long declaredSize,
            Map<String, Object> metadata,
            Instant retentionUntil,
            CommandContext context) {
        Objects.requireNonNull(context, "context must not be null");
        ConsentResult consent = getConsentUseCase.getConsent(context.tenantId(), consentId);
        if (consent.evidencePolicy() != EvidencePolicy.REQUIRED || consent.currentRevisionId() == null) {
            throw new EvidenceException(ErrorCode.CONFLICT,
                    "A finalized evidence-required consent is needed before creating an upload session");
        }
        if (consent.status().name().matches("REVOKED|EXPIRED|REJECTED|CANCELLED")) {
            throw new EvidenceException(ErrorCode.CONFLICT,
                    "Evidence cannot be attached to a terminal consent");
        }
        if (declaredSize < 0) {
            throw new EvidenceException(ErrorCode.VALIDATION_ERROR, "declaredSize must not be negative");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(java.time.Duration.ofMinutes(30));
        String safeFilename = sanitizedFilename(originalFilename);
        String safeMediaType = requireText(mediaType, "mediaType");
        String safeEvidenceType = requireText(evidenceType, "evidenceType");
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        jdbc.update("""
                insert into evidence_upload_session(
                    id, tenant_id, consent_id, revision_id, evidence_type, original_filename,
                    media_type, declared_size, metadata, retention_until, status, expires_at,
                    created_at, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CREATED', ?, ?, ?)
                """, UuidUtils.toBytes(id), context.tenantId(), UuidUtils.toBytes(consentId),
                UuidUtils.toBytes(consent.currentRevisionId()), safeEvidenceType,
                safeFilename, safeMediaType, declaredSize, json(safeMetadata), toTimestamp(retentionUntil),
                toTimestamp(expiresAt), toTimestamp(now), context.actorId());

        appendAudit("CREATE_EVIDENCE_UPLOAD_SESSION", consentId, context,
                Map.of("sessionId", id.toString(), "evidenceType", safeEvidenceType));

        return new EvidenceUploadSessionResult(id, consentId, consent.currentRevisionId(), safeEvidenceType,
                safeFilename, safeMediaType, declaredSize, safeMetadata, retentionUntil,
                "CREATED", null, expiresAt, now);
    }

    @Override
    @Transactional
    public EvidenceArtifactResult uploadSessionContent(
            UUID consentId, UUID sessionId, InputStream content, CommandContext context) {
        Objects.requireNonNull(context, "context must not be null");
        EvidenceUploadSessionResult session = loadUploadSession(context.tenantId(), consentId, sessionId);

        if (!"CREATED".equals(session.status()) && !"UPLOADING".equals(session.status())) {
            throw new EvidenceException(ErrorCode.CONFLICT,
                    "Upload session is not writable in status " + session.status());
        }

        if (!session.expiresAt().isAfter(clock.instant())) {
            jdbc.update("update evidence_upload_session set status='EXPIRED' where id=? and tenant_id=?",
                    UuidUtils.toBytes(sessionId), context.tenantId());
            throw new EvidenceException(ErrorCode.CONFLICT, "Upload session has expired");
        }

        jdbc.update("update evidence_upload_session set status='UPLOADING' where id=? and tenant_id=?",
                UuidUtils.toBytes(sessionId), context.tenantId());

        EvidenceArtifactResult artifact = upload(consentId, session.evidenceType(), session.originalFilename(),
                session.mediaType(), session.declaredSize(), content, null, session.metadata(),
                session.retentionUntil(), context);

        jdbc.update("""
                update evidence_upload_session set status='COMPLETED', artifact_id=?, completed_at=?
                 where id=? and tenant_id=? and consent_id=?
                """, UuidUtils.toBytes(artifact.id()), toTimestamp(clock.instant()), UuidUtils.toBytes(sessionId)
                , context.tenantId(), UuidUtils.toBytes(consentId));

        appendAudit("COMPLETE_EVIDENCE_UPLOAD_SESSION", consentId, context,
                Map.of("sessionId", sessionId.toString(), "artifactId", artifact.id().toString()));

        return artifact;
    }

    @Override
    @Transactional
    public EvidenceUploadSessionResult cancelUploadSession(
            UUID consentId, UUID sessionId, CommandContext context) {
        Objects.requireNonNull(context, "context must not be null");
        EvidenceUploadSessionResult session = loadUploadSession(context.tenantId(), consentId, sessionId);

        if ("COMPLETED".equals(session.status())) {
            throw new EvidenceException(ErrorCode.CONFLICT, "Completed upload session cannot be cancelled");
        }

        Instant now = clock.instant();

        jdbc.update("""
                update evidence_upload_session set status='CANCELLED', cancelled_at=?
                 where id=? and tenant_id=? and consent_id=?
                """, toTimestamp(now), UuidUtils.toBytes(sessionId), context.tenantId(), UuidUtils.toBytes(consentId));

        appendAudit("CANCEL_EVIDENCE_UPLOAD_SESSION", consentId, context,
                Map.of("sessionId", sessionId.toString()));

        return new EvidenceUploadSessionResult(session.id(), session.consentId(), session.revisionId(),
                session.evidenceType(), session.originalFilename(), session.mediaType(), session.declaredSize(),
                session.metadata(), session.retentionUntil(), "CANCELLED", session.artifactId(),
                session.expiresAt(), session.createdAt());
    }

    @Override
    @Transactional
    public EvidenceArtifactResult upload(
            UUID consentId,
            String evidenceType,
            String originalFilename,
            String mediaType,
            long declaredSize,
            InputStream content,
            UUID supersedesArtifactId,
            Map<String, Object> metadata,
            Instant retentionUntil,
            CommandContext context) {
        Objects.requireNonNull(context, "context must not be null");
        ConsentResult consent = getConsentUseCase.getConsent(context.tenantId(), consentId);

        if (consent.evidencePolicy() != EvidencePolicy.REQUIRED) {
            throw new EvidenceException(ErrorCode.CONFLICT,
                    "Evidence artifacts are not required for this consent");
        }

        if (consent.currentRevisionId() == null) {
            throw new EvidenceException(ErrorCode.CONFLICT,
                    "Consent content revision must be finalized before evidence attachment");
        }

        if (consent.status().name().matches("REVOKED|EXPIRED|REJECTED|CANCELLED")) {
            throw new EvidenceException(ErrorCode.CONFLICT,
                    "Evidence cannot be attached to a terminal consent");

        }
        evidenceType = requireText(evidenceType, "evidenceType");
        originalFilename = sanitizedFilename(originalFilename);
        mediaType = requireText(mediaType, "mediaType");

        if (declaredSize < 0) {
            throw new EvidenceException(ErrorCode.VALIDATION_ERROR, "declaredSize must not be negative");
        }

        extensionEngine.execute(ExtensionPoint.BEFORE_EVIDENCE_ATTACHMENT,
                extensionContext(consent, evidenceType, context,
                        Map.of("filename", originalFilename, "mediaType", mediaType)));

        UUID bundleId = findBundleId(context.tenantId(), consentId, consent.currentRevisionId());
        Instant now = clock.instant();
        boolean newBundle = bundleId == null;

        if (newBundle) {
            bundleId = UUID.randomUUID();
            jdbc.update("""
                    insert into consent_evidence_bundle(
                        id, tenant_id, consent_id, revision_id, evidence_type, status,
                        retention_until, legal_hold, created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, 'PENDING', ?, 0, ?, ?, ?, ?)
                    """, UuidUtils.toBytes(bundleId), context.tenantId(), UuidUtils.toBytes(consentId), consent.currentRevisionId(), evidenceType,
                    toTimestamp(retentionUntil), toTimestamp(now), context.actorId(), toTimestamp(now), context.actorId());
        } else {
            jdbc.update("""
                    update consent_evidence_bundle
                       set status = 'PENDING', evidence_type = ?,
                           retention_until = coalesce(?, retention_until),
                           updated_at = ?, updated_by = ?, version = version + 1
                     where id = ? and tenant_id = ?
                    """, evidenceType, toTimestamp(retentionUntil), toTimestamp(now), context.actorId(), UuidUtils.toBytes(bundleId), context.tenantId());
        }

        EvidenceStoragePort.StoredObject stored = null;

        try {
            stored = storage.store(context.tenantId(), consentId, originalFilename, mediaType, content);
            registerRollbackCleanup(stored.objectKey());

            if (declaredSize > 0 && declaredSize != stored.sizeBytes()) {
                throw new EvidenceException(ErrorCode.VALIDATION_ERROR,
                        "Uploaded size does not match declared size");
            }

            EvidenceMalwareScanner.ScanResult scanResult;

            try (InputStream scanContent = storage.load(stored.objectKey())) {
                scanResult = malwareScanner.scan(scanContent, mediaType);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Unable to close evidence scan stream", exception);
            }

            if (scanResult == EvidenceMalwareScanner.ScanResult.INFECTED) {
                throw new EvidenceException(ErrorCode.CONFLICT,
                        "Evidence artifact was rejected by malware scanning");
            }

            if (scanResult == EvidenceMalwareScanner.ScanResult.FAILED && verificationRequiresCleanScan) {
                throw new EvidenceException(ErrorCode.CONFLICT,
                        "Evidence artifact malware scanning failed");
            }

            if (supersedesArtifactId != null) {
                int updated = jdbc.update("""
                        update consent_evidence_artifact
                           set status = 'SUPERSEDED', superseded_at = ?, superseded_by = ?
                         where id = ? and tenant_id = ? and bundle_id = ? and status = 'ACTIVE'
                        """, toTimestamp(now), context.actorId(), supersedesArtifactId, context.tenantId(), UuidUtils.toBytes(bundleId));

                if (updated != 1) {
                    throw new EvidenceException(ErrorCode.NOT_FOUND,
                            "Artifact to supersede was not found or is not active");
                }
            }

            UUID artifactId = UUID.randomUUID();

            jdbc.update("""
                    insert into consent_evidence_artifact(
                        id, tenant_id, bundle_id, evidence_type, storage_provider,
                        storage_container, object_key, original_filename, media_type,
                        size_bytes, sha256, status, malware_scan_status,
                        supersedes_artifact_id, metadata, created_at, created_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?,
                            ?, ?, ?, ?)
                    """, UuidUtils.toBytes(artifactId), context.tenantId(), UuidUtils.toBytes(bundleId), evidenceType,
                    stored.storageProvider(), stored.storageContainer(), stored.objectKey(),
                    originalFilename, mediaType, stored.sizeBytes(), stored.sha256(), scanResult.name(),
                    supersedesArtifactId, json(metadata == null ? Map.of() : metadata), toTimestamp(now), context.actorId());

            if (newBundle) {
                linkBundleUseCase.linkEvidenceBundle(new LinkEvidenceBundleCommand(
                        consentId, bundleId, consent.currentRevisionId(), EvidenceStatus.PENDING, context));
            } else {
                updateStatusUseCase.updateEvidenceStatus(new UpdateEvidenceStatusCommand(
                        consentId, bundleId, EvidenceStatus.PENDING, context));
            }

            appendAccess(context, consentId, bundleId, artifactId,
                    supersedesArtifactId == null ? "UPLOAD" : "SUPERSEDE");

            appendAudit("UPLOAD_EVIDENCE", consentId, context, Map.of(
                    "bundleId", bundleId.toString(), "artifactId", artifactId.toString(),
                    "evidenceType", evidenceType, "sha256", stored.sha256(),
                    "sizeBytes", stored.sizeBytes()));

            outboxWriter.append(new EvidenceArtifactAttachedEvent(
                    UUID.randomUUID(), context.tenantId(), consentId, bundleId, artifactId,
                    consent.currentRevisionId(), evidenceType, stored.sha256(), stored.sizeBytes(), now), context);

            if (supersedesArtifactId != null) {
                outboxWriter.append(new EvidenceArtifactSupersededEvent(
                        UUID.randomUUID(), context.tenantId(), consentId, bundleId,
                        supersedesArtifactId, artifactId, now), context);
            }

            ExtensionContext attachmentContext = extensionContext(
                    consent, evidenceType, context,
                    Map.of("bundleId", bundleId.toString(), "artifactId", artifactId.toString()));

            extensionEngine.execute(ExtensionPoint.AFTER_EVIDENCE_ATTACHMENT, attachmentContext);

            if (supersedesArtifactId != null) {
                extensionEngine.execute(ExtensionPoint.AFTER_ARTIFACT_SUPERSEDED,
                        extensionContext(consent, evidenceType, context,
                                Map.of("bundleId", bundleId.toString(),
                                        "artifactId", artifactId.toString(),
                                        "supersededArtifactId", supersedesArtifactId.toString())));
            }

            return findArtifact(context.tenantId(), consentId, artifactId);
        } catch (RuntimeException failure) {
            if (stored != null) {
                try { storage.delete(stored.objectKey()); } catch (RuntimeException ignored) { }
            }

            throw failure;
        }
    }

    @Override
    @Transactional
    public EvidenceBundleResult verify(
            UUID consentId,
            UUID bundleId,
            UUID artifactId,
            String result,
            String verificationMethod,
            String reasonCode,
            String reasonDetail,
            Map<String, Object> verificationData,
            CommandContext context) {
        ConsentResult consent = getConsentUseCase.getConsent(context.tenantId(), consentId);
        result = requireText(result, "result").toUpperCase();

        if (!result.equals("VERIFIED") && !result.equals("REJECTED")) {
            throw new EvidenceException(ErrorCode.VALIDATION_ERROR,
                    "Verification result must be VERIFIED or REJECTED");
        }

        requireBundle(context.tenantId(), consentId, bundleId);

        if (artifactId != null) {
            EvidenceArtifactResult artifact = findArtifact(context.tenantId(), consentId, artifactId);

            if (!artifact.status().equals("ACTIVE")) {
                throw new EvidenceException(ErrorCode.CONFLICT, "Only an active artifact can be verified");
            }

            if (artifact.malwareScanStatus().equals("INFECTED")) {
                throw new EvidenceException(ErrorCode.CONFLICT, "Infected evidence artifact cannot be verified");
            }

            if (verificationRequiresCleanScan && !artifact.malwareScanStatus().equals("CLEAN")) {
                throw new EvidenceException(ErrorCode.CONFLICT,
                        "Evidence verification requires malware scan status CLEAN");
            }
        }

        extensionEngine.execute(ExtensionPoint.BEFORE_EVIDENCE_VERIFICATION,
                extensionContext(consent, null, context,
                        Map.of("bundleId", bundleId.toString(), "result", result)));

        Instant now = clock.instant();
        UUID verificationId = UUID.randomUUID();

        jdbc.update("""
                insert into consent_evidence_verification(
                    id, tenant_id, bundle_id, artifact_id, result, verifier_id,
                    verification_method, reason_code, reason_detail, verification_data, verified_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UuidUtils.toBytes(verificationId), context.tenantId(), UuidUtils.toBytes(bundleId), UuidUtils.toBytes(artifactId), result,
                context.actorId(), requireText(verificationMethod, "verificationMethod"),
                blankToNull(reasonCode), blankToNull(reasonDetail),
                json(verificationData == null ? Map.of() : verificationData), toTimestamp(now));

        jdbc.update("""
                update consent_evidence_bundle
                   set status = ?, verified_at = ?, verified_by = ?,
                       updated_at = ?, updated_by = ?, version = version + 1
                 where id = ? and tenant_id = ? and consent_id = ?
                """, result, toTimestamp(now), context.actorId(), toTimestamp(now), context.actorId(),
                UuidUtils.toBytes(bundleId), context.tenantId(), UuidUtils.toBytes(consentId));

        updateStatusUseCase.updateEvidenceStatus(new UpdateEvidenceStatusCommand(
                consentId, bundleId, EvidenceStatus.valueOf(result), context));

        appendAccess(context, consentId, bundleId, artifactId,
                result.equals("VERIFIED") ? "VERIFY" : "REJECT");

        appendAudit(result.equals("VERIFIED") ? "VERIFY_EVIDENCE" : "REJECT_EVIDENCE",
                consentId, context, Map.of("bundleId", bundleId.toString(),
                        "verificationId", verificationId.toString()));

        outboxWriter.append(new EvidenceVerificationCompletedEvent(
                UUID.randomUUID(), context.tenantId(), consentId, bundleId, artifactId,
                verificationId, result, verificationMethod, now), context);

        extensionEngine.execute(result.equals("VERIFIED")
                        ? ExtensionPoint.AFTER_EVIDENCE_VERIFIED : ExtensionPoint.AFTER_EVIDENCE_REJECTED,
                extensionContext(consent, null, context,
                        Map.of("bundleId", bundleId.toString(), "verificationId", verificationId.toString())));
        return getBundle(context.tenantId(), consentId);
    }

    @Override
    @Transactional(readOnly = true)
    public EvidenceBundleResult getBundle(String tenantId, UUID consentId) {
        List<EvidenceBundleResult> results = jdbc.query("""
                select b.id, b.tenant_id, b.consent_id, b.revision_id, b.evidence_type, b.status,
                       b.retention_until, b.legal_hold, b.created_at, b.created_by,
                       b.verified_at, b.verified_by
                  from consent_evidence_bundle b
                  join consent c on c.id = b.consent_id and c.tenant_id = b.tenant_id
                 where b.tenant_id = ? and b.consent_id = ? and c.evidence_bundle_id = b.id
                """, (rs, n) -> new EvidenceBundleResult(
                        UuidUtils.fromBytes(rs.getBytes("id")), rs.getString("tenant_id"),
                        UuidUtils.fromBytes(rs.getBytes("consent_id")), UuidUtils.fromBytes(rs.getBytes("revision_id")),
                        rs.getString("evidence_type"), rs.getString("status"),
                        rs.getTimestamp("retention_until") == null ? null : rs.getTimestamp("retention_until").toInstant(),
                        rs.getBoolean("legal_hold"), rs.getTimestamp("created_at").toInstant(),
                        rs.getString("created_by"),
                        rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toInstant(),
                        rs.getString("verified_by"), List.of(), List.of()), tenantId, consentId);

        if (results.isEmpty()) {
            throw new EvidenceException(ErrorCode.NOT_FOUND, "Evidence bundle not found");
        }

        EvidenceBundleResult base = results.getFirst();

        return new EvidenceBundleResult(base.id(), base.tenantId(), base.consentId(), base.revisionId(),
                base.evidenceType(), base.status(), base.retentionUntil(), base.legalHold(),
                base.createdAt(), base.createdBy(), base.verifiedAt(), base.verifiedBy(),
                listArtifacts(tenantId, consentId), listVerifications(tenantId, base.id()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceArtifactResult> listArtifacts(String tenantId, UUID consentId) {
        return jdbc.query("""
                select a.id, a.bundle_id, a.evidence_type, a.storage_provider, a.object_key,
                       a.original_filename, a.media_type, a.size_bytes, a.sha256, a.status,
                       a.malware_scan_status, a.supersedes_artifact_id, a.metadata,
                       a.created_at, a.created_by
                  from consent_evidence_artifact a
                  join consent_evidence_bundle b on b.id = a.bundle_id
                 where a.tenant_id = ? and b.consent_id = ?
                 order by a.created_at desc
                """, (rs, n) -> mapArtifact(rs), tenantId, UuidUtils.toBytes(consentId));
    }

    @Override
    @Transactional
    public EvidenceDownload download(UUID consentId, UUID artifactId, CommandContext context) {
        EvidenceArtifactResult artifact = findArtifact(context.tenantId(), consentId, artifactId);

        String objectKey = jdbc.queryForObject("""
                select a.object_key
                  from consent_evidence_artifact a
                  join consent_evidence_bundle b on b.id = a.bundle_id
                 where a.tenant_id = ? and b.consent_id = ? and a.id = ?
                """, String.class, context.tenantId(), UuidUtils.toBytes(consentId), UuidUtils.toBytes(artifactId));

        appendAccess(context, consentId, artifact.bundleId(), artifactId, "DOWNLOAD");

        appendAudit("DOWNLOAD_EVIDENCE", consentId, context,
                Map.of("bundleId", artifact.bundleId().toString(), "artifactId", artifactId.toString()));

        return new EvidenceDownload(
                () -> storage.load(objectKey),
                artifact.originalFilename(), artifact.mediaType(), artifact.sizeBytes());
    }

    @Override
    @Transactional
    public EvidenceBundleResult setLegalHold(
            UUID consentId, UUID bundleId, boolean legalHold, CommandContext context) {
        requireBundle(context.tenantId(), consentId, bundleId);

        jdbc.update("""
                update consent_evidence_bundle
                   set legal_hold = ?, updated_at = ?, updated_by = ?, version = version + 1
                 where id = ? and tenant_id = ? and consent_id = ?
                """, legalHold, toTimestamp(clock.instant()), context.actorId(), UuidUtils.toBytes(bundleId),
                context.tenantId(), UuidUtils.toBytes(consentId));

        appendAccess(context, consentId, bundleId, null, "LEGAL_HOLD");

        appendAudit("SET_EVIDENCE_LEGAL_HOLD", consentId, context,
                Map.of("bundleId", bundleId.toString(), "legalHold", legalHold));

        return getBundle(context.tenantId(), consentId);
    }

    private void registerRollbackCleanup(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    try {
                        storage.delete(objectKey);
                    } catch (RuntimeException ignored) {
                        // Metadata transaction is already rolled back. Operations should reconcile storage alerts.
                    }
                }
            }
        });
    }

    private UUID findBundleId(String tenantId, UUID consentId, UUID revisionId) {
        List<UUID> ids = jdbc.query("""
                select id from consent_evidence_bundle
                 where tenant_id = ? and consent_id = ? and revision_id = ?
                """, (rs, n) -> UuidUtils.fromBytes(rs.getBytes(1)), tenantId,
                UuidUtils.toBytes(consentId), UuidUtils.toBytes(revisionId));
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private void requireBundle(String tenantId, UUID consentId, UUID bundleId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from consent_evidence_bundle
                 where tenant_id = ? and consent_id = ? and id = ?
                """, Integer.class, tenantId, UuidUtils.toBytes(consentId), UuidUtils.toBytes(bundleId));

        if (count == null || count != 1) {
            throw new EvidenceException(ErrorCode.NOT_FOUND, "Evidence bundle not found");
        }
    }

    private EvidenceArtifactResult findArtifact(String tenantId, UUID consentId, UUID artifactId) {
        List<EvidenceArtifactResult> rows = jdbc.query("""
                select a.id, a.bundle_id, a.evidence_type, a.storage_provider, a.object_key,
                       a.original_filename, a.media_type, a.size_bytes, a.sha256, a.status,
                       a.malware_scan_status, a.supersedes_artifact_id, a.metadata,
                       a.created_at, a.created_by
                  from consent_evidence_artifact a
                  join consent_evidence_bundle b on b.id = a.bundle_id
                 where a.tenant_id = ? and b.consent_id = ? and a.id = ?
                """, (rs, n) -> mapArtifact(rs), tenantId, UuidUtils.toBytes(consentId), UuidUtils.toBytes(artifactId));

        if (rows.isEmpty()) {
            throw new EvidenceException(ErrorCode.NOT_FOUND, "Evidence artifact not found");
        }

        return rows.getFirst();
    }

    private EvidenceArtifactResult mapArtifact(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new EvidenceArtifactResult(
                UuidUtils.fromBytes(rs.getBytes("id")), UuidUtils.fromBytes(rs.getBytes("bundle_id")),
                rs.getString("evidence_type"), rs.getString("storage_provider"), rs.getString("object_key"),
                rs.getString("original_filename"), rs.getString("media_type"), rs.getLong("size_bytes"),
                rs.getString("sha256"), rs.getString("status"), rs.getString("malware_scan_status"),
                UuidUtils.fromBytes(rs.getBytes("supersedes_artifact_id")), readMap(rs.getString("metadata")),
                rs.getTimestamp("created_at").toInstant(), rs.getString("created_by"));
    }

    private List<EvidenceVerificationResult> listVerifications(String tenantId, UUID bundleId) {
        return jdbc.query("""
                select id, bundle_id, artifact_id, result, verifier_id, verification_method,
                       reason_code, reason_detail, verification_data, verified_at
                  from consent_evidence_verification
                 where tenant_id = ? and bundle_id = ?
                 order by verified_at desc
                """, (rs, n) -> new EvidenceVerificationResult(
                        UuidUtils.fromBytes(rs.getBytes("id")), UuidUtils.fromBytes(rs.getBytes("bundle_id")),
                        UuidUtils.fromBytes(rs.getBytes("artifact_id")), rs.getString("result"),
                        rs.getString("verifier_id"), rs.getString("verification_method"),
                        rs.getString("reason_code"), rs.getString("reason_detail"),
                        readMap(rs.getString("verification_data")), rs.getTimestamp("verified_at").toInstant()),
                tenantId, UuidUtils.toBytes(bundleId));
    }

    private void appendAccess(CommandContext context, UUID consentId, UUID bundleId, UUID artifactId, String action) {
        jdbc.update("""
                insert into evidence_access_log(
                    id, tenant_id, consent_id, bundle_id, artifact_id, action,
                    actor_id, actor_type, source_system, correlation_id, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UuidUtils.toBytes(UUID.randomUUID()), context.tenantId(), UuidUtils.toBytes(consentId),
                UuidUtils.toBytes(bundleId), UuidUtils.toBytes(artifactId),
                action, context.actorId(), context.actorType().name(), context.sourceSystem(),
                context.correlationId(), toTimestamp(clock.instant()));
    }

    private EvidenceUploadSessionResult loadUploadSession(String tenantId, UUID consentId, UUID sessionId) {
        List<EvidenceUploadSessionResult> rows = jdbc.query("""
                select id, consent_id, revision_id, evidence_type, original_filename, media_type,
                       declared_size, metadata, retention_until, status, artifact_id,
                       expires_at, created_at
                  from evidence_upload_session
                 where tenant_id=? and consent_id=? and id=?
                """, (rs, rowNum) -> new EvidenceUploadSessionResult(
                UuidUtils.fromBytes(rs.getBytes("id")), UuidUtils.fromBytes(rs.getBytes("consent_id")),
                UuidUtils.fromBytes(rs.getBytes("revision_id")), rs.getString("evidence_type"),
                rs.getString("original_filename"), rs.getString("media_type"), rs.getLong("declared_size"),
                readMap(rs.getString("metadata")),
                rs.getTimestamp("retention_until") == null ? null : rs.getTimestamp("retention_until").toInstant(),
                rs.getString("status"), UuidUtils.fromBytes(rs.getBytes("artifact_id")),
                rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("created_at").toInstant()),
                tenantId, UuidUtils.toBytes(consentId), UuidUtils.toBytes(sessionId));

        if (rows.isEmpty()) {
            throw new EvidenceException(ErrorCode.NOT_FOUND, "Evidence upload session not found");
        }

        return rows.getFirst();
    }

    private void appendAudit(String action, UUID consentId, CommandContext context, Map<String, Object> details) {
        auditWriter.append(new AuditRecord(
                UUID.randomUUID(), context.tenantId(), "Consent", consentId, action, "SUCCESS",
                context.actorId(), context.actorType(), context.sourceSystem(), context.correlationId(),
                context.requestId(), details, clock.instant()));
    }

    private static ExtensionContext extensionContext(ConsentResult consent, String evidenceType, CommandContext context, Map<String, Object> attributes) {
        return new ExtensionContext(context.tenantId(), consent.id(), consent.consentType(), consent.clientId(),
                consent.acquisitionChannel(), consent.captureMethod(), evidenceType,
                context, attributes, Map.of());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new EvidenceException(ErrorCode.VALIDATION_ERROR, "Invalid evidence metadata JSON");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        try {
            return json == null ? Map.of() : objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid evidence JSON stored in database", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new EvidenceException(ErrorCode.VALIDATION_ERROR, field + " must not be blank");
        }

        return value.trim();
    }

    private static String sanitizedFilename(String value) {
        String filename = requireText(value, "originalFilename");
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);

        if (filename.length() > 300) filename = filename.substring(filename.length() - 300);

        if (filename.equals(".") || filename.equals("..")) {
            throw new EvidenceException(ErrorCode.VALIDATION_ERROR, "Invalid filename");
        }

        return filename;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
