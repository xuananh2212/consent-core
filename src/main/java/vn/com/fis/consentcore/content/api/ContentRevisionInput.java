package vn.com.fis.consentcore.content.api;

import java.util.List;
import java.util.Map;

public record ContentRevisionInput(
        String purposeCode,
        List<ContentPermission> permissions,
        List<ContentResource> resources,
        List<ContentConstraint> constraints,
        List<ContentObligation> obligations,
        Map<String, Object> presentationSnapshot
) {
    public ContentRevisionInput {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        resources = resources == null ? List.of() : List.copyOf(resources);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        obligations = obligations == null ? List.of() : List.copyOf(obligations);
        presentationSnapshot = presentationSnapshot == null ? Map.of() : Map.copyOf(presentationSnapshot);
    }

    /** Backward-compatible constructor for clients created before obligations were introduced. */
    public ContentRevisionInput(
            String purposeCode,
            List<ContentPermission> permissions,
            List<ContentResource> resources,
            List<ContentConstraint> constraints,
            Map<String, Object> presentationSnapshot) {
        this(purposeCode, permissions, resources, constraints, List.of(), presentationSnapshot);
    }
}
