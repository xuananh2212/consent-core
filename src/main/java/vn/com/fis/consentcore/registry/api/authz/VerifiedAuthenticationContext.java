package vn.com.fis.consentcore.registry.api.authz;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Normalized authentication result supplied by a trusted Flow Manager.
 * Raw IAM JWTs, passwords, OTPs and biometric material must never be carried in this contract.
 */
public record VerifiedAuthenticationContext(
        String subjectRef,
        String issuer,
        Instant authenticationTime,
        String acr,
        List<String> amr,
        String evidenceRef,
        String evidenceHash,
        String verificationProfile
) {
    public VerifiedAuthenticationContext {
        subjectRef = requireText(subjectRef, "subjectRef", 200);
        issuer = requireText(issuer, "issuer", 300);
        authenticationTime = Objects.requireNonNull(authenticationTime, "authenticationTime must not be null");
        acr = trimToNull(acr, "acr", 300);
        amr = amr == null ? List.of() : amr.stream()
                .map(value -> requireText(value, "amr", 100))
                .distinct()
                .toList();
        evidenceRef = trimToNull(evidenceRef, "evidenceRef", 500);
        evidenceHash = trimToNull(evidenceHash, "evidenceHash", 200);
        verificationProfile = requireText(verificationProfile, "verificationProfile", 200);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private static String trimToNull(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) return null;
        return requireText(value, field, maxLength);
    }
}
