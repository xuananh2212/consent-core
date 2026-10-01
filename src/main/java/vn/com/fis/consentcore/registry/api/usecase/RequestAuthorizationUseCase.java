package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.command.RequestAuthorizationCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface RequestAuthorizationUseCase {
    ConsentResult requestAuthorization(RequestAuthorizationCommand command);
}
