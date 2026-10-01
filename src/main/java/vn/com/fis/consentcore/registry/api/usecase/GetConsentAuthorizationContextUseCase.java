package vn.com.fis.consentcore.registry.api.usecase;

import java.util.UUID;
import vn.com.fis.consentcore.registry.api.authz.ConsentAuthorizationContext;

public interface GetConsentAuthorizationContextUseCase {
    ConsentAuthorizationContext getAuthorizationContext(String tenantId, UUID consentId);
}
