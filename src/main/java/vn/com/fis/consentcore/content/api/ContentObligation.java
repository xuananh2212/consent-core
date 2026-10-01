package vn.com.fis.consentcore.content.api;

import java.util.Map;

/**
 * A structured duty that applies after consent is authorized, for example notification,
 * masking, deletion, or usage-reporting requirements.
 */
public record ContentObligation(
        String obligationType,
        Map<String, Object> parameters
) {
    public ContentObligation {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
