package vn.com.fis.consentcore.reference.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.shared.api.CommandContext;

public interface ReferenceDataApi {
    UUID upsertPermission(PermissionDefinition definition, CommandContext context);
    List<PermissionDefinition> listPermissions(String tenantId);

    record PermissionDefinition(
            String permissionCode,
            String displayName,
            String description,
            int schemaVersion,
            Map<String, Object> attributesSchema,
            boolean active) {
        public PermissionDefinition {
            attributesSchema = attributesSchema == null ? Map.of() : Map.copyOf(attributesSchema);
        }
    }
}
