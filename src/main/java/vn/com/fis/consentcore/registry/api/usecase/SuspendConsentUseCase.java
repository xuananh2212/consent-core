package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.command.SuspendConsentCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface SuspendConsentUseCase {
    ConsentResult suspend(SuspendConsentCommand command);
}
