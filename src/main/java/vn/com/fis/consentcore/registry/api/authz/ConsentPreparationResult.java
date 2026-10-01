package vn.com.fis.consentcore.registry.api.authz;

import java.util.List;
import java.util.Map;
import vn.com.fis.consentcore.enrichment.api.SelectionCandidate;

/** Result of preparing a consent for a PSU decision. */
public record ConsentPreparationResult(
        String preparationStatus,
        ConsentAuthorizationContext authorizationContext,
        String selectionContextHash,
        List<SelectionRequirement> selectionRequirements,
        String reasonCode,
        String messageKey,
        Boolean retryable,
        String correlationId
) {
    public static final String DENIED = "DENIED";
    public static final String SELECTION_REQUIRED = "SELECTION_REQUIRED";
    public static final String PREPARED = "PREPARED";

    public ConsentPreparationResult {
        selectionRequirements = selectionRequirements == null ? List.of() : List.copyOf(selectionRequirements);
    }

    /** Backward-compatible result constructor for non-denial outcomes. */
    public ConsentPreparationResult(
            String preparationStatus,
            ConsentAuthorizationContext authorizationContext,
            String selectionContextHash,
            List<SelectionRequirement> selectionRequirements) {
        this(preparationStatus, authorizationContext, selectionContextHash, selectionRequirements,
                null, null, null, null);
    }

    public static ConsentPreparationResult denied(
            ConsentAuthorizationContext authorizationContext,
            String reasonCode,
            String messageKey,
            boolean retryable,
            String correlationId) {
        return new ConsentPreparationResult(DENIED, authorizationContext, null, List.of(),
                reasonCode, messageKey, retryable, correlationId);
    }

    public record SelectionRequirement(
            String requirementCode,
            String dataType,
            boolean required,
            List<SelectionCandidate> candidates,
            Map<String,Object> presentation
    ) {
        public SelectionRequirement {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            presentation = presentation == null ? Map.of() : Map.copyOf(presentation);
        }
    }
}
