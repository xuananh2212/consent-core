package vn.com.fis.consentcore.enrichment.api;

import java.util.Map;
import java.util.UUID;

/** Domain-neutral request passed to a replaceable backend provider. */
public record DataProviderRequest(
        String tenantId,
        UUID consentId,
        String consentType,
        String requirementCode,
        String dataType,
        DataPurpose purpose,
        String subjectRef,
        String clientId,
        Map<String, Object> parameters
) {
    public DataProviderRequest {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
