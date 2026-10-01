package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.command.AuthorizeConsentCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface AuthorizeConsentFromFlowUseCase {
    ConsentResult authorizeFromFlow(AuthorizeConsentCommand command);
}
