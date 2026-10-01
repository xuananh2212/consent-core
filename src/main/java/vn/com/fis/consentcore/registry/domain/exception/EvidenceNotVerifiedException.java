package vn.com.fis.consentcore.registry.domain.exception;

import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.EvidenceStatus;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class EvidenceNotVerifiedException extends BusinessException {
    public EvidenceNotVerifiedException(UUID consentId, EvidenceStatus evidenceStatus) {
        super(
                ErrorCode.CONSENT_EVIDENCE_NOT_VERIFIED,
                "Required consent evidence has not been verified",
                Map.of("consentId", consentId.toString(), "evidenceStatus", evidenceStatus.name())
        );
    }
}
