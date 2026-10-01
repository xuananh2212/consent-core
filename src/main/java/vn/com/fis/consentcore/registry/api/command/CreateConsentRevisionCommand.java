package vn.com.fis.consentcore.registry.api.command;

import java.util.UUID;
import vn.com.fis.consentcore.content.api.ContentRevisionInput;
import vn.com.fis.consentcore.shared.api.CommandContext;

public record CreateConsentRevisionCommand(
        UUID consentId,
        ContentRevisionInput content,
        CommandContext context
) {
}
