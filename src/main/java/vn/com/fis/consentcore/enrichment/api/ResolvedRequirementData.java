package vn.com.fis.consentcore.enrichment.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ResolvedRequirementData(
        String requirementCode,
        String dataType,
        DataPurpose purpose,
        boolean required,
        int schemaVersion,
        String providerCode,
        List<SelectionCandidate> candidates,
        Map<String, Object> data,
        Map<String, Object> requirementConfiguration,
        String normalizedDataHash,
        String candidateSetHash,
        Instant resolvedAt
) {
    public ResolvedRequirementData {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        data = data == null ? Map.of() : Map.copyOf(data);
        requirementConfiguration = requirementConfiguration == null ? Map.of() : Map.copyOf(requirementConfiguration);
    }
}
