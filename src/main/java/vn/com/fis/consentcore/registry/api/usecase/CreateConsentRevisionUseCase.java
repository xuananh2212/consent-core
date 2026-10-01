package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.command.CreateConsentRevisionCommand;
import vn.com.fis.consentcore.content.api.ContentRevisionResult;

public interface CreateConsentRevisionUseCase {
    ContentRevisionResult createRevision(CreateConsentRevisionCommand command);
}
