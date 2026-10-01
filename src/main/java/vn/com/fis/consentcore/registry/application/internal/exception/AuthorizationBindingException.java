package vn.com.fis.consentcore.registry.application.internal.exception;

import java.util.Map;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class AuthorizationBindingException extends BusinessException {
    public AuthorizationBindingException(String reason) {
        super(ErrorCode.CONSENT_AUTHORIZATION_BINDING_INVALID,
                "Consent authorization binding validation failed", Map.of("reason", reason));
    }
}
