package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.command.RevokeConsentCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface RevokeConsentUseCase {
    ConsentResult revoke(RevokeConsentCommand command);
}
