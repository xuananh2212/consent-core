package vn.com.fis.consentcore.evidence.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EvidenceUploadSessionResult(
        UUID id,
        UUID consentId,
        UUID revisionId,
        String evidenceType,
        String originalFilename,
        String mediaType,
        long declaredSize,
        Map<String, Object> metadata,
        Instant retentionUntil,
        String status,
        UUID artifactId,
        Instant expiresAt,
        Instant createdAt
) {
    public EvidenceUploadSessionResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
