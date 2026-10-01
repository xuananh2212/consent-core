package vn.com.fis.consentcore.content.internal;

import java.util.UUID;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public final class ContentRevisionNotFoundException extends BusinessException {
    public ContentRevisionNotFoundException(UUID revisionId) {
        super(ErrorCode.NOT_FOUND, "Consent content revision not found: " + revisionId);
    }
}
