package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.command.LinkEvidenceBundleCommand;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface LinkEvidenceBundleUseCase {
    ConsentResult linkEvidenceBundle(LinkEvidenceBundleCommand command);
}
