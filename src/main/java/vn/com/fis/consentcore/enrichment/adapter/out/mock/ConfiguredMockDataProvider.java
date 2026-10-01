package vn.com.fis.consentcore.enrichment.adapter.out.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.com.fis.consentcore.enrichment.api.ConsentDataProvider;
import vn.com.fis.consentcore.enrichment.api.DataProviderRequest;
import vn.com.fis.consentcore.enrichment.api.DataProviderResponse;
import vn.com.fis.consentcore.enrichment.api.DataPurpose;
import vn.com.fis.consentcore.enrichment.api.ProviderConfiguration;
import vn.com.fis.consentcore.enrichment.api.SelectionCandidate;

/** Generic configured mock. ACCOUNT_LIST is only one possible dataType using this adapter. */
@Component
public class ConfiguredMockDataProvider implements ConsentDataProvider {
    public static final String ADAPTER_KEY = "CONFIGURED_MOCK";

    @Override
    public String adapterKey() {
        return ADAPTER_KEY;
    }

    @Override
    public DataProviderResponse fetch(DataProviderRequest request, ProviderConfiguration configuration) {
        int schemaVersion = intValue(configuration.providerConfiguration().get("schemaVersion"), 1);
        if (request.purpose() == DataPurpose.SELECTION) {
            Object configured = configuration.providerConfiguration().get("candidates");
            if (!(configured instanceof List<?> values)) return DataProviderResponse.selection(schemaVersion, List.of());
            List<SelectionCandidate> candidates = new ArrayList<>();
            for (Object value : values) {
                if (!(value instanceof Map<?,?> map)) continue;
                String id = string(map.get("id"));
                String resourceType = stringOr(map.get("resourceType"), request.dataType());
                String label = stringOr(map.get("label"), id);
                Map<String,Object> attributes = objectMap(map.get("attributes"));
                candidates.add(new SelectionCandidate(id, resourceType, label, attributes));
            }
            return DataProviderResponse.selection(schemaVersion, candidates);
        }
        return DataProviderResponse.data(schemaVersion, objectMap(configuration.providerConfiguration().get("data")));
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String string(Object value) {
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("mock candidate id must not be blank");
        return value.toString().trim();
    }

    private static String stringOr(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    private static Map<String,Object> objectMap(Object value) {
        if (!(value instanceof Map<?,?> map)) return Map.of();
        Map<String,Object> result = new LinkedHashMap<>();
        map.forEach((key,item) -> result.put(String.valueOf(key), item));
        return Map.copyOf(result);
    }
}
