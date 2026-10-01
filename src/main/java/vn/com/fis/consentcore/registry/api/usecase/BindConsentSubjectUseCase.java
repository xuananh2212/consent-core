package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.command.BindConsentSubjectCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface BindConsentSubjectUseCase {
    ConsentResult bindSubject(BindConsentSubjectCommand command);
}