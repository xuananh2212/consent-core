package vn.com.fis.consentcore.registry.api.command;

import java.util.UUID;
import vn.com.fis.consentcore.shared.api.CommandContext;

public record RevokeConsentCommand(
        UUID consentId,
        String reasonCode,
        String reasonDetail,
        CommandContext context
) {
}
