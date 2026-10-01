package vn.com.fis.consentcore.evidence.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EvidenceBundleResult(
        UUID id,
        String tenantId,
        UUID consentId,
        UUID revisionId,
        String evidenceType,
        String status,
        Instant retentionUntil,
        boolean legalHold,
        Instant createdAt,
        String createdBy,
        Instant verifiedAt,
        String verifiedBy,
        List<EvidenceArtifactResult> artifacts,
        List<EvidenceVerificationResult> verifications
) {
}
