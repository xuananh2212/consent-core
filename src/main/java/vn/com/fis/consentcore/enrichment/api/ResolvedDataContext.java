package vn.com.fis.consentcore.enrichment.api;

import java.util.List;

public record ResolvedDataContext(
        List<ResolvedRequirementData> requirements,
        String selectionContextHash
) {
    public ResolvedDataContext {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
    }

    public List<ResolvedRequirementData> selectionRequirements() {
        return requirements.stream().filter(item -> item.purpose() == DataPurpose.SELECTION).toList();
    }

    public boolean hasSelectionRequirements() {
        return requirements.stream().anyMatch(item -> item.purpose() == DataPurpose.SELECTION);
    }
}
