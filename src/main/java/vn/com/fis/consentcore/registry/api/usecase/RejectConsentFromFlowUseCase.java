package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.command.RejectConsentCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface RejectConsentFromFlowUseCase {
    ConsentResult rejectFromFlow(RejectConsentCommand command);
}
