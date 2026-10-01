package vn.com.fis.consentcore.content.api;

import java.util.Map;

public record ContentPermission(String permissionCode, Map<String, Object> attributes) {
    public ContentPermission {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
