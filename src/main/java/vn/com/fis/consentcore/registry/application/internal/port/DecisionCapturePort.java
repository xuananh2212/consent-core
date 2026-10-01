package vn.com.fis.consentcore.registry.application.internal.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.CaptureMethod;

public interface DecisionCapturePort {
    UUID append(DecisionCaptureRecord record);

    record DecisionCaptureRecord(
            UUID id,
            String tenantId,
            UUID consentId,
            UUID revisionId,
            String outcome,
            String decisionMakerId,
            String capturedBy,
            CaptureMethod captureMethod,
            String captureLocation,
            String authorizationReference,
            Map<String, Object> authenticationContext,
            Instant decidedAt,
            Instant createdAt
    ) {
    }
}
