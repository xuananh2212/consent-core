package vn.com.fis.consentcore.registry.api.authz;

/**
 * Technical provenance for an interaction. These references are audit/reconciliation metadata only;
 * they never drive the Consent aggregate lifecycle.
 */
public record InteractionProvenance(
        String sourceSystem,
        String sourceInteractionRef,
        String authorizationServerRef,
        String asTransactionRef
) {
    public InteractionProvenance {
        sourceSystem = requireText(sourceSystem, "sourceSystem", 100);
        sourceInteractionRef = requireText(sourceInteractionRef, "sourceInteractionRef", 200);
        authorizationServerRef = trimToNull(authorizationServerRef, "authorizationServerRef", 150);
        asTransactionRef = trimToNull(asTransactionRef, "asTransactionRef", 300);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        return normalized;
    }

    private static String trimToNull(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) return null;
        return requireText(value, field, maxLength);
    }
}
