package vn.com.fis.consentcore.evidence.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EvidenceVerificationResult(
        UUID id,
        UUID bundleId,
        UUID artifactId,
        String result,
        String verifierId,
        String verificationMethod,
        String reasonCode,
        String reasonDetail,
        Map<String, Object> verificationData,
        Instant verifiedAt
) {
}
