package vn.com.fis.consentcore.registry.api.command;

import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.registry.api.authz.InteractionProvenance;
import vn.com.fis.consentcore.registry.api.authz.VerifiedAuthenticationContext;
import vn.com.fis.consentcore.shared.api.CommandContext;

public record RejectConsentCommand(
        UUID consentId,
        String reasonCode,
        String reasonDetail,
        String idempotencyKey,
        Long expectedVersion,
        Integer expectedRevisionNo,
        String expectedContentHash,
        String clientId,
        String subjectRef,
        VerifiedAuthenticationContext authenticationContext,
        InteractionProvenance interactionProvenance,
        Instant decidedAt,
        CommandContext context
) {
    /** Backward-compatible command for non-Flow-Manager/manual callers. */
    public RejectConsentCommand(UUID consentId, String reasonCode, String reasonDetail, CommandContext context) {
        this(consentId, reasonCode, reasonDetail, null, null, null, null, null, null, null, null, null, context);
    }

    public boolean flowBound() {
        return interactionProvenance != null || authenticationContext != null || idempotencyKey != null;
    }
}
