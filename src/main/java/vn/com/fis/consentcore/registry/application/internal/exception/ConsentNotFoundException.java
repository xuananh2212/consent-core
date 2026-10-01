package vn.com.fis.consentcore.registry.application.internal.exception;

import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class ConsentNotFoundException extends BusinessException {
    public ConsentNotFoundException(String tenantId, UUID consentId) {
        super(
                ErrorCode.CONSENT_NOT_FOUND,
                "Consent was not found",
                Map.of("tenantId", tenantId, "consentId", consentId.toString())
        );
    }
}
