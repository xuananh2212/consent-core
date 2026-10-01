package vn.com.fis.consentcore.registry.api.command;

import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.EvidenceStatus;
import vn.com.fis.consentcore.shared.api.CommandContext;

public record UpdateEvidenceStatusCommand(
        UUID consentId,
        UUID bundleId,
        EvidenceStatus evidenceStatus,
        CommandContext context
) {
}
