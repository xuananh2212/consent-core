package vn.com.fis.consentcore.enrichment.internal;

import java.util.Map;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

public class DataEnrichmentException extends BusinessException {
    public DataEnrichmentException(ErrorCode code, String message) {
        super(code, message);
    }

    public DataEnrichmentException(ErrorCode code, String message, Map<String,Object> details) {
        super(code, message, details);
    }
}
