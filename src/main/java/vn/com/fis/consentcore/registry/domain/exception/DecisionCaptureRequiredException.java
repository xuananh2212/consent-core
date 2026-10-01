package vn.com.fis.consentcore.registry.domain.exception;

import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class DecisionCaptureRequiredException extends BusinessException {
    public DecisionCaptureRequiredException(UUID consentId) {
        super(ErrorCode.CONSENT_DECISION_REQUIRED,
                "A decision capture bound to the current content revision is required",
                Map.of("consentId", consentId.toString()));
    }
}
