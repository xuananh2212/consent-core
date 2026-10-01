package vn.com.fis.consentcore.enrichment.adapter.out.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.com.fis.consentcore.enrichment.api.ConsentDataProvider;
import vn.com.fis.consentcore.enrichment.api.DataProviderRequest;
import vn.com.fis.consentcore.enrichment.api.DataProviderResponse;
import vn.com.fis.consentcore.enrichment.api.DataPurpose;
import vn.com.fis.consentcore.enrichment.api.ProviderConfiguration;
import vn.com.fis.consentcore.enrichment.api.SelectionCandidate;

/** Generic JSON-over-HTTP provider. Source payload is normalized before crossing the provider contract. */
@Component
public class RestJsonConsentDataProvider implements ConsentDataProvider {
    public static final String ADAPTER_KEY = "REST_JSON";
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper mapper;

    public RestJsonConsentDataProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String adapterKey() {
        return ADAPTER_KEY;
    }

    @Override
    public DataProviderResponse fetch(DataProviderRequest request, ProviderConfiguration configuration) {
        if (configuration.credentialReference() != null && !configuration.credentialReference().isBlank()) {
            throw new IllegalStateException("credentialReference requires a deployment-specific credential resolver");
        }
        URI uri = buildUri(configuration.endpointTemplate(), request, configuration.providerConfiguration());
        validateEndpoint(uri, configuration.providerConfiguration());
        String method = configuration.httpMethod() == null || configuration.httpMethod().isBlank()
                ? "GET" : configuration.httpMethod().trim().toUpperCase(Locale.ROOT);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(configuration.timeoutMs()))
                .header("Accept", "application/json");
        staticHeaders(configuration.providerConfiguration()).forEach(builder::header);
        if ("POST".equals(method)) {
            Object bodyTemplate = configuration.providerConfiguration().getOrDefault("requestBody", Map.of());
            Object body = expandObject(bodyTemplate, request);
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json(body), StandardCharsets.UTF_8));
        } else if ("GET".equals(method)) {
            builder.GET();
        } else {
            throw new IllegalArgumentException("REST_JSON supports GET or POST only");
        }

        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("backend returned HTTP " + response.statusCode());
            }
            int maxBytes = intValue(configuration.providerConfiguration().get("maxResponseBytes"), DEFAULT_MAX_RESPONSE_BYTES);
            if (response.body().getBytes(StandardCharsets.UTF_8).length > maxBytes) {
                throw new IllegalStateException("backend response exceeds configured maximum size");
            }
            Object payload = mapper.readValue(response.body(), Object.class);
            Object selected = navigate(payload, string(configuration.mappingConfiguration().get("responsePath")));
            int schemaVersion = intValue(configuration.mappingConfiguration().get("schemaVersion"), 1);
            if (request.purpose() == DataPurpose.SELECTION) {
                return DataProviderResponse.selection(schemaVersion, mapCandidates(selected, request, configuration));
            }
            return DataProviderResponse.data(schemaVersion, asCanonicalData(selected));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("backend call interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("backend call failed", ex);
        }
    }

    private List<SelectionCandidate> mapCandidates(
            Object payload, DataProviderRequest request, ProviderConfiguration configuration) {
        if (!(payload instanceof Collection<?> items)) {
            throw new IllegalArgumentException("SELECTION mapping expects an array/collection response");
        }
        Map<String,Object> mapping = configuration.mappingConfiguration();
        String idField = stringOr(mapping.get("idField"), "id");
        String labelField = stringOr(mapping.get("labelField"), "label");
        String resourceType = stringOr(mapping.get("resourceType"), request.dataType());
        List<String> attributeFields = stringList(mapping.get("attributeFields"));
        List<SelectionCandidate> result = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?,?> source)) continue;
            String id = requiredString(source.get(idField), idField);
            String label = stringOr(source.get(labelField), id);
            Map<String,Object> attributes = new LinkedHashMap<>();
            for (String field : attributeFields) if (source.containsKey(field)) attributes.put(field, source.get(field));
            result.add(new SelectionCandidate(id, resourceType, label, attributes));
        }
        return result;
    }

    private static URI buildUri(String template, DataProviderRequest request, Map<String,Object> configuration) {
        if (template == null || template.isBlank()) throw new IllegalArgumentException("endpointTemplate is required for REST_JSON");
        String value = template;
        value = value.replace("{tenantId}", encode(request.tenantId()));
        value = value.replace("{consentId}", encode(request.consentId().toString()));
        value = value.replace("{consentType}", encode(request.consentType()));
        value = value.replace("{dataType}", encode(request.dataType()));
        value = value.replace("{subjectRef}", encode(request.subjectRef()));
        value = value.replace("{clientId}", encode(request.clientId()));
        for (Map.Entry<String,Object> entry : request.parameters().entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", encode(entry.getValue() == null ? "" : entry.getValue().toString()));
        }
        return URI.create(value);
    }

    private static void validateEndpoint(URI uri, Map<String,Object> configuration) {
        if (!Set.of("http", "https").contains(uri.getScheme())) throw new IllegalArgumentException("backend URI must use http or https");
        if (uri.getHost() == null || uri.getHost().isBlank()) throw new IllegalArgumentException("backend URI must contain a host");
        List<String> allowedHosts = stringList(configuration.get("allowedHosts"));
        if (!allowedHosts.isEmpty() && allowedHosts.stream().noneMatch(host -> host.equalsIgnoreCase(uri.getHost()))) {
            throw new IllegalArgumentException("backend host is not in provider allowedHosts");
        }
    }

    private static Map<String,String> staticHeaders(Map<String,Object> configuration) {
        Object raw = configuration.get("headers");
        if (!(raw instanceof Map<?,?> values)) return Map.of();
        Map<String,String> result = new LinkedHashMap<>();
        values.forEach((key,value) -> {
            String name = String.valueOf(key);
            if (name.equalsIgnoreCase("Authorization") || name.equalsIgnoreCase("Cookie")) {
                throw new IllegalArgumentException("secret-bearing Authorization/Cookie headers must use a credential resolver");
            }
            result.put(name, String.valueOf(value));
        });
        return Map.copyOf(result);
    }

    private static Object navigate(Object payload, String path) {
        if (path == null || path.isBlank()) return payload;
        Object current = payload;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?,?> map)) throw new IllegalArgumentException("responsePath cannot be resolved: " + path);
            current = map.get(part);
        }
        return current;
    }

    private static Map<String,Object> asCanonicalData(Object value) {
        if (value instanceof Map<?,?> map) {
            Map<String,Object> result = new LinkedHashMap<>();
            map.forEach((key,item) -> result.put(String.valueOf(key), item));
            return Map.copyOf(result);
        }
        return Map.of("items", value == null ? List.of() : value);
    }

    private static Object expandObject(Object value, DataProviderRequest request) {
        if (value instanceof String text) return expandText(text, request);
        if (value instanceof Map<?,?> map) {
            Map<String,Object> result = new LinkedHashMap<>();
            map.forEach((key,item) -> result.put(String.valueOf(key), expandObject(item, request)));
            return result;
        }
        if (value instanceof Collection<?> collection) return collection.stream().map(item -> expandObject(item, request)).toList();
        return value;
    }

    private static String expandText(String text, DataProviderRequest request) {
        return text.replace("{tenantId}", safe(request.tenantId()))
                .replace("{consentId}", request.consentId().toString())
                .replace("{consentType}", safe(request.consentType()))
                .replace("{dataType}", safe(request.dataType()))
                .replace("{subjectRef}", safe(request.subjectRef()))
                .replace("{clientId}", safe(request.clientId()));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("requestBody cannot be serialized", ex); }
    }

    private static String encode(String value) {
        return URLEncoder.encode(safe(value), StandardCharsets.UTF_8).replace("+", "%20");
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String string(Object value) { return value == null ? null : value.toString(); }
    private static String stringOr(Object value, String fallback) { return value == null || value.toString().isBlank() ? fallback : value.toString(); }
    private static String requiredString(Object value, String field) { if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("missing mapped field: " + field); return value.toString(); }
    private static int intValue(Object value, int fallback) { return value instanceof Number number ? number.intValue() : fallback; }
    private static List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().filter(java.util.Objects::nonNull).map(Object::toString).filter(item -> !item.isBlank()).toList();
    }
}
