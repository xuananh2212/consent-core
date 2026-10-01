package vn.com.fis.consentcore.registry.api.usecase;

import java.util.Optional;
import vn.com.fis.consentcore.registry.api.authz.CommandResult;

public interface GetConsentCommandResultUseCase {
    Optional<CommandResult> getCommandResult(String tenantId, String operation, String idempotencyKey);
}
