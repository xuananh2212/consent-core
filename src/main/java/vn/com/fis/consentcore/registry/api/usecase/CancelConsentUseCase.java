package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.command.CancelConsentCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface CancelConsentUseCase {
    ConsentResult cancel(CancelConsentCommand command);
}
