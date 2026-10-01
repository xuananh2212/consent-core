package vn.com.fis.consentcore.enrichment.api;

import java.util.List;
import java.util.Map;

/** Canonical provider response; source-specific DTOs must be normalized before crossing this contract. */
public record DataProviderResponse(
        int schemaVersion,
        List<SelectionCandidate> candidates,
        Map<String, Object> data
) {
    public DataProviderResponse {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static DataProviderResponse selection(int schemaVersion, List<SelectionCandidate> candidates) {
        return new DataProviderResponse(schemaVersion, candidates, Map.of());
    }

    public static DataProviderResponse data(int schemaVersion, Map<String, Object> data) {
        return new DataProviderResponse(schemaVersion, List.of(), data);
    }
}
