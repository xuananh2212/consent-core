package vn.com.fis.consentcore.registry.domain.model;

import java.time.Instant;

public record ConsentRegistrationData(
        String tenantId,
        String externalRequestId,
        String consentType,
        String subjectId,
        String clientId,
        String purpose,
        Instant validFrom,
        Instant validUntil,
        AcquisitionChannel acquisitionChannel,
        CaptureMethod captureMethod,
        String sourceSystem,
        String externalConsentId,
        EvidencePolicy evidencePolicy,
        boolean trustedSourceAuthorization,
        Instant capturedAt,
        String capturedBy,
        String captureLocation,
        String importBatchReference
) {
}
