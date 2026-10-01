package vn.com.fis.consentcore.enrichment.api;

import java.util.Map;

/** Canonical, data-minimized item that may be presented to a PSU for selection. */
public record SelectionCandidate(
        String id,
        String resourceType,
        String label,
        Map<String, Object> attributes
) {
    public SelectionCandidate {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("candidate id must not be blank");
        if (resourceType == null || resourceType.isBlank()) throw new IllegalArgumentException("resourceType must not be blank");
        if (label == null || label.isBlank()) label = id;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
