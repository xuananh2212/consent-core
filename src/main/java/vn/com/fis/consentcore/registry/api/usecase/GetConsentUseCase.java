package vn.com.fis.consentcore.registry.api.usecase;

import java.util.UUID;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface GetConsentUseCase {
    ConsentResult getConsent(String tenantId, UUID consentId);
}
