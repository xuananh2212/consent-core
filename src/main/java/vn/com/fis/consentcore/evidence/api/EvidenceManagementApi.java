package vn.com.fis.consentcore.evidence.api;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.shared.api.CommandContext;

public interface EvidenceManagementApi {
    EvidenceUploadSessionResult createUploadSession(
            UUID consentId,
            String evidenceType,
            String originalFilename,
            String mediaType,
            long declaredSize,
            Map<String, Object> metadata,
            Instant retentionUntil,
            CommandContext context);

    EvidenceArtifactResult uploadSessionContent(
            UUID consentId,
            UUID sessionId,
            InputStream content,
            CommandContext context);

    EvidenceUploadSessionResult cancelUploadSession(
            UUID consentId,
            UUID sessionId,
            CommandContext context);

    EvidenceArtifactResult upload(
            UUID consentId,
            String evidenceType,
            String originalFilename,
            String mediaType,
            long declaredSize,
            InputStream content,
            UUID supersedesArtifactId,
            Map<String, Object> metadata,
            Instant retentionUntil,
            CommandContext context);

    EvidenceBundleResult verify(
            UUID consentId,
            UUID bundleId,
            UUID artifactId,
            String result,
            String verificationMethod,
            String reasonCode,
            String reasonDetail,
            Map<String, Object> verificationData,
            CommandContext context);

    EvidenceBundleResult getBundle(String tenantId, UUID consentId);
    List<EvidenceArtifactResult> listArtifacts(String tenantId, UUID consentId);
    EvidenceDownload download(UUID consentId, UUID artifactId, CommandContext context);
    EvidenceBundleResult setLegalHold(UUID consentId, UUID bundleId, boolean legalHold, CommandContext context);
}
