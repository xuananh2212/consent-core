package vn.com.fis.consentcore.policy.api;

import java.util.List;

/**
 * Channel-neutral authentication assurance required before a digital consent decision is accepted.
 * Flow Manager is responsible for obtaining and cryptographically verifying the authentication evidence.
 */
public record RequiredAssurance(
        boolean authenticationRequired,
        String requiredAcr,
        List<String> requiredAmr,
        Long maxAuthenticationAgeSeconds,
        boolean authenticationEvidenceRequired,
        boolean stepUpAllowed
) {
    public RequiredAssurance {
        requiredAcr = trimToNull(requiredAcr, 300, "requiredAcr");
        requiredAmr = requiredAmr == null ? List.of() : requiredAmr.stream()
                .map(value -> requireText(value, 100, "requiredAmr"))
                .distinct()
                .toList();
        if (maxAuthenticationAgeSeconds != null && maxAuthenticationAgeSeconds <= 0) {
            throw new IllegalArgumentException("maxAuthenticationAgeSeconds must be positive");
        }
    }

    public static RequiredAssurance digitalDefault() {
        return new RequiredAssurance(true, null, List.of(), null, false, true);
    }

    private static String requireText(String value, int max, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must not exceed " + max + " characters");
        return normalized;
    }

    private static String trimToNull(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        return requireText(value, max, field);
    }
}
