package vn.com.fis.consentcore.registry.api.query;

import java.time.Instant;
import vn.com.fis.consentcore.registry.domain.model.ConsentStatus;

public record ConsentSearchCriteria(
        String tenantId,
        ConsentStatus status,
        String consentType,
        String subjectId,
        String clientId,
        String sourceSystem,
        String externalConsentId,
        Instant createdFrom,
        Instant createdTo,
        int page,
        int size
) {
    public ConsentSearchCriteria {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 200) throw new IllegalArgumentException("size must be between 1 and 200");
        if (createdFrom != null && createdTo != null && !createdTo.isAfter(createdFrom)) {
            throw new IllegalArgumentException("createdTo must be after createdFrom");
        }
    }
}
