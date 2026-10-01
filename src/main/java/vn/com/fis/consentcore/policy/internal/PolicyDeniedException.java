package vn.com.fis.consentcore.policy.internal;

import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class PolicyDeniedException extends BusinessException {
    public PolicyDeniedException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
