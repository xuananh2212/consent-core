package vn.com.fis.consentcore.content.api;

import java.util.Map;

public record ContentResource(String resourceType, String resourceId, Map<String, Object> attributes) {
    public ContentResource {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
