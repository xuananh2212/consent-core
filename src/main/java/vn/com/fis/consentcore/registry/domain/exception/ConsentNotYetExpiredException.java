package vn.com.fis.consentcore.registry.domain.exception;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class ConsentNotYetExpiredException extends BusinessException {
    public ConsentNotYetExpiredException(UUID consentId, Instant validUntil, Instant now) {
        super(
                ErrorCode.CONSENT_NOT_YET_EXPIRED,
                "Consent validity has not ended",
                Map.of(
                        "consentId", consentId.toString(),
                        "validUntil", validUntil.toString(),
                        "evaluatedAt", now.toString()
                )
        );
    }
}
