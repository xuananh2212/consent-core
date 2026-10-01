package vn.com.fis.consentcore.registry.api.command;

import java.time.Instant;
import vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel;
import vn.com.fis.consentcore.registry.domain.model.CaptureMethod;
import vn.com.fis.consentcore.registry.domain.model.EvidencePolicy;
import vn.com.fis.consentcore.content.api.ContentRevisionInput;
import vn.com.fis.consentcore.shared.api.CommandContext;

public record RegisterConsentCommand(
        String idempotencyKey,
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
        String trustedAuthorizationReference,
        Instant capturedAt,
        String capturedBy,
        String captureLocation,
        String importBatchReference,
        ContentRevisionInput content,
        CommandContext context
) {
}
