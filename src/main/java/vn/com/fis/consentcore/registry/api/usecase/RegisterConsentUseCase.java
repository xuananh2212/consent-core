package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.command.RegisterConsentCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface RegisterConsentUseCase {
    ConsentResult register(RegisterConsentCommand command);
}
