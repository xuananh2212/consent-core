package vn.com.fis.consentcore.registry.api.usecase;

import java.util.List;
import java.util.UUID;
import vn.com.fis.consentcore.registry.api.result.ConsentHistoryResult;

public interface GetConsentHistoryUseCase {
    List<ConsentHistoryResult> getHistory(String tenantId, UUID consentId);
}
