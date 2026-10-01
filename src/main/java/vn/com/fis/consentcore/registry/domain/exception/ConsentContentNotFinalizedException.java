package vn.com.fis.consentcore.registry.domain.exception;

import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class ConsentContentNotFinalizedException extends BusinessException {
    public ConsentContentNotFinalizedException(UUID consentId) {
        super(ErrorCode.CONSENT_CONTENT_NOT_FINALIZED,
                "Consent does not have a finalized structured content revision",
                Map.of("consentId", consentId.toString()));
    }
}
