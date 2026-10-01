package vn.com.fis.consentcore.registry.api.command;

import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.EvidenceStatus;
import vn.com.fis.consentcore.shared.api.CommandContext;

public record LinkEvidenceBundleCommand(
        UUID consentId,
        UUID bundleId,
        UUID revisionId,
        EvidenceStatus evidenceStatus,
        CommandContext context
) {
}
