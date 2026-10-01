package vn.com.fis.consentcore.content.internal;

import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class ContentValidationException extends BusinessException {
    public ContentValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}
