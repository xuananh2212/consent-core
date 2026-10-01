package vn.com.fis.consentcore.registry.application.internal.exception;

import java.util.Map;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class IdempotencyKeyReuseException extends BusinessException {
    public IdempotencyKeyReuseException(String idempotencyKey) {
        super(
                ErrorCode.IDEMPOTENCY_KEY_REUSED,
                "Idempotency key has already been used with a different request",
                Map.of("idempotencyKey", idempotencyKey)
        );
    }
}
