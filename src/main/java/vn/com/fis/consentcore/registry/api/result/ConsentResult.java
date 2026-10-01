package vn.com.fis.consentcore.registry.api.result;

import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel;
import vn.com.fis.consentcore.registry.domain.model.CaptureMethod;
import vn.com.fis.consentcore.registry.domain.model.ConsentStatus;
import vn.com.fis.consentcore.registry.domain.model.EvidencePolicy;
import vn.com.fis.consentcore.registry.domain.model.EvidenceStatus;

public record ConsentResult(
        UUID id,
        String tenantId,
        String externalRequestId,
        String consentType,
        String subjectId,
        String clientId,
        String purpose,
        ConsentStatus status,
        Instant validFrom,
        Instant validUntil,
        AcquisitionChannel acquisitionChannel,
        CaptureMethod captureMethod,
        String sourceSystem,
        String externalConsentId,
        EvidencePolicy evidencePolicy,
        EvidenceStatus evidenceStatus,
        boolean trustedSourceAuthorization,
        String authorizationReference,
        Instant capturedAt,
        String capturedBy,
        String captureLocation,
        String importBatchReference,
        UUID currentRevisionId,
        Integer currentRevisionNo,
        UUID decisionId,
        UUID evidenceBundleId,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        long version
) {
}
