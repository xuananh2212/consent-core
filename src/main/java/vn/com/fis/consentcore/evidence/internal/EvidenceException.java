package vn.com.fis.consentcore.evidence.internal;

import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class EvidenceException extends BusinessException {
    public EvidenceException(ErrorCode code, String message) {
        super(code, message);
    }
}
