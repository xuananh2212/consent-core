package vn.com.fis.consentcore.registry.domain.exception;

import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class InvalidConsentValidityException extends BusinessException {
    public InvalidConsentValidityException(String message) {
        super(ErrorCode.CONSENT_VALIDITY_INVALID, message);
    }
}
