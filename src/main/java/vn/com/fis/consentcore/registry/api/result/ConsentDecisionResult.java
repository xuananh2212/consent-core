package vn.com.fis.consentcore.registry.api.result;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.CaptureMethod;

public record ConsentDecisionResult(
        UUID id,
        UUID consentId,
        UUID revisionId,
        String outcome,
        String subjectId,
        String capturedBy,
        CaptureMethod captureMethod,
        String captureLocation,
        String authorizationReference,
        Map<String, Object> decisionData,
        Instant decidedAt,
        Instant createdAt) {
    public ConsentDecisionResult {
        decisionData = decisionData == null ? Map.of() : Map.copyOf(decisionData);
    }
}
