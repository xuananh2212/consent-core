package vn.com.fis.consentcore.registry.api.command;

import java.util.UUID;
import vn.com.fis.consentcore.shared.api.CommandContext;

public record ExpireConsentCommand(
        UUID consentId,
        CommandContext context
) {
}
