package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.command.ExpireConsentCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface ExpireConsentUseCase {
    ConsentResult expire(ExpireConsentCommand command);
}
