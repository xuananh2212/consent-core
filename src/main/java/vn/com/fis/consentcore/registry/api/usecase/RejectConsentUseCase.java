package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.command.RejectConsentCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface RejectConsentUseCase {
    ConsentResult reject(RejectConsentCommand command);
}
