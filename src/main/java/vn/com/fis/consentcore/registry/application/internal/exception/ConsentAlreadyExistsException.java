package vn.com.fis.consentcore.registry.application.internal.exception;

import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class ConsentAlreadyExistsException extends BusinessException {
    public ConsentAlreadyExistsException(String tenantId, String sourceSystem, String externalConsentId, UUID consentId) {
        super(
                ErrorCode.CONSENT_ALREADY_EXISTS,
                "Consent already exists for the external source reference",
                Map.of(
                        "tenantId", tenantId,
                        "sourceSystem", sourceSystem,
                        "externalConsentId", externalConsentId,
                        "existingConsentId", consentId.toString()
                )
        );
    }
}
