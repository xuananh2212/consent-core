package vn.com.fis.consentcore.content.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ContentRevisionResult(
        UUID id,
        String tenantId,
        UUID consentId,
        int revisionNo,
        String purposeCode,
        String contentHash,
        String status,
        List<ContentPermission> permissions,
        List<ContentResource> resources,
        List<ContentConstraint> constraints,
        List<ContentObligation> obligations,
        Map<String, Object> presentationSnapshot,
        Instant createdAt,
        String createdBy,
        Instant finalizedAt,
        String finalizedBy
) {
    public ContentRevisionResult {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        resources = resources == null ? List.of() : List.copyOf(resources);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        obligations = obligations == null ? List.of() : List.copyOf(obligations);
        presentationSnapshot = presentationSnapshot == null ? Map.of() : Map.copyOf(presentationSnapshot);
    }

    /** Backward-compatible constructor for callers created before obligations were introduced. */
    public ContentRevisionResult(
            UUID id,
            String tenantId,
            UUID consentId,
            int revisionNo,
            String purposeCode,
            String contentHash,
            String status,
            List<ContentPermission> permissions,
            List<ContentResource> resources,
            List<ContentConstraint> constraints,
            Map<String, Object> presentationSnapshot,
            Instant createdAt,
            String createdBy,
            Instant finalizedAt,
            String finalizedBy) {
        this(id, tenantId, consentId, revisionNo, purposeCode, contentHash, status,
                permissions, resources, constraints, List.of(), presentationSnapshot,
                createdAt, createdBy, finalizedAt, finalizedBy);
    }
}
