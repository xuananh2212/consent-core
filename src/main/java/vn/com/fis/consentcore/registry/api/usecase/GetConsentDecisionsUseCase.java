package vn.com.fis.consentcore.registry.api.usecase;

import java.util.List;
import java.util.UUID;
import vn.com.fis.consentcore.registry.api.result.ConsentDecisionResult;

public interface GetConsentDecisionsUseCase {
    List<ConsentDecisionResult> getDecisions(String tenantId, UUID consentId);
}
