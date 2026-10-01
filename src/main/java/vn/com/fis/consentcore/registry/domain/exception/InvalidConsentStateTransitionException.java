package vn.com.fis.consentcore.registry.domain.exception;

import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.ConsentStatus;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class InvalidConsentStateTransitionException extends BusinessException {
    public InvalidConsentStateTransitionException(UUID consentId, ConsentStatus current, ConsentStatus target) {
        super(
                ErrorCode.CONSENT_INVALID_STATE_TRANSITION,
                "Consent cannot transition from " + current + " to " + target,
                Map.of(
                        "consentId", consentId.toString(),
                        "currentStatus", current.name(),
                        "targetStatus", target.name()
                )
        );
    }
}
