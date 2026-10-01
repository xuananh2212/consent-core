package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.command.UpdateEvidenceStatusCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface UpdateEvidenceStatusUseCase {
    ConsentResult updateEvidenceStatus(UpdateEvidenceStatusCommand command);
}
