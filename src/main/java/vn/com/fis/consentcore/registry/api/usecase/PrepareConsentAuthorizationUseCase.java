package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.authz.ConsentPreparationResult;
import vn.com.fis.consentcore.registry.api.command.PrepareAuthorizationCommand;

public interface PrepareConsentAuthorizationUseCase {
    ConsentPreparationResult prepareAuthorization(PrepareAuthorizationCommand command);
}
