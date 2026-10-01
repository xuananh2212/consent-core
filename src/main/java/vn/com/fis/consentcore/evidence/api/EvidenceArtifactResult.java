package vn.com.fis.consentcore.evidence.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EvidenceArtifactResult(
        UUID id,
        UUID bundleId,
        String evidenceType,
        String storageProvider,
        String objectKey,
        String originalFilename,
        String mediaType,
        long sizeBytes,
        String sha256,
        String status,
        String malwareScanStatus,
        UUID supersedesArtifactId,
        Map<String, Object> metadata,
        Instant createdAt,
        String createdBy
) {
}
