package vn.com.fis.consentcore.extension.internal;

import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class ExtensionExecutionException extends BusinessException {
    public ExtensionExecutionException(String message, Throwable cause) {
        super(ErrorCode.CONFLICT, message);
        initCause(cause);
    }
}
