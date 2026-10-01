package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.authz.AuthorizationValidationRequest;
import vn.com.fis.consentcore.registry.api.authz.AuthorizationValidationResult;

public interface ValidateConsentAuthorizationUseCase {
    AuthorizationValidationResult validateAuthorization(String tenantId, AuthorizationValidationRequest request);
}
