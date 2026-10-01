package vn.com.fis.consentcore.registry.application.internal.exception;

import java.util.Map;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public class PreparationStaleException extends BusinessException {
    public PreparationStaleException(String message, Map<String,Object> details) {
        super(ErrorCode.CONSENT_PREPARATION_STALE, message, details);
    }
}
