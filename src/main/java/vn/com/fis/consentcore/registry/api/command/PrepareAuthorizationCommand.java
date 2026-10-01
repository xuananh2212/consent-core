package vn.com.fis.consentcore.registry.api.command;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.shared.api.CommandContext;

/**
 * Flow Manager command that asks Consent Core to prepare a consent for a PSU decision.
 * clientId and scopes are trusted authorization facts and are cross-checked/used only by Core-owned rules.
 */
public record PrepareAuthorizationCommand(
        UUID consentId,
        String idempotencyKey,
        String clientId,
        List<String> scopes,
        String subjectRef,
        String selectionContextHash,
        Map<String, List<String>> selections,
        String interactionMode,
        CommandContext context
) {
    public PrepareAuthorizationCommand {
        scopes = normalizeScopes(scopes);
        selections = selections == null ? Map.of() : selections.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() == null ? List.of() : List.copyOf(entry.getValue())));
    }

    public PrepareAuthorizationCommand(
            UUID consentId,
            String idempotencyKey,
            String clientId,
            List<String> scopes,
            String subjectRef,
            String selectionContextHash,
            Map<String, List<String>> selections,
            CommandContext context) {
        this(consentId, idempotencyKey, clientId, scopes, subjectRef, selectionContextHash, selections, null, context);
    }

    public PrepareAuthorizationCommand(
            UUID consentId,
            String idempotencyKey,
            String clientId,
            String subjectRef,
            String selectionContextHash,
            Map<String, List<String>> selections,
            CommandContext context) {
        this(consentId, idempotencyKey, clientId, List.of(), subjectRef, selectionContextHash, selections, context);
    }

    public PrepareAuthorizationCommand(UUID consentId, String idempotencyKey, String clientId, CommandContext context) {
        this(consentId, idempotencyKey, clientId, List.of(), null, null, Map.of(), context);
    }

    private static List<String> normalizeScopes(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }
}