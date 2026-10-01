package vn.com.fis.consentcore.enrichment.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import vn.com.fis.consentcore.shared.api.CommandContext;

public record ConsentDataResolutionRequest(
        UUID consentId,
        String consentType,
        ResolutionPhase phase,
        String subjectRef,
        String clientId,
        List<String> effectiveScopes,
        Set<DataPurpose> purposes,
        Map<String, Object> parameters,
        CommandContext context
) {
    public ConsentDataResolutionRequest {
        effectiveScopes = normalizeScopes(effectiveScopes);
        purposes = purposes == null ? Set.of() : Set.copyOf(purposes);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    /** Backward-compatible constructor: no scope selectors and resolve all configured purposes. */
    public ConsentDataResolutionRequest(
            UUID consentId,
            String consentType,
            ResolutionPhase phase,
            String subjectRef,
            String clientId,
            Map<String, Object> parameters,
            CommandContext context) {
        this(consentId, consentType, phase, subjectRef, clientId, List.of(), Set.of(), parameters, context);
    }

    private static List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) return List.of();
        return scopes.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .sorted()
                .toList();
    }
}
