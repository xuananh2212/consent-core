package vn.com.fis.consentcore.registry.api.command;

import java.time.Instant;
import java.util.UUID;
import vn.com.fis.consentcore.registry.api.authz.InteractionProvenance;
import vn.com.fis.consentcore.registry.api.authz.VerifiedAuthenticationContext;
import vn.com.fis.consentcore.shared.api.CommandContext;

public record AuthorizeConsentCommand(
        UUID consentId,
        String authorizationReference,
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
    /** Backward-compatible command for non-Flow-Manager callers. */
    public AuthorizeConsentCommand(UUID consentId, String authorizationReference, CommandContext context) {
        this(consentId, authorizationReference, null, null, null, null, null, null, null, null, null, context);
    }

    public boolean flowBound() {
        return interactionProvenance != null || authenticationContext != null || idempotencyKey != null;
    }
}
