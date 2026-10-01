package vn.com.fis.consentcore.reference.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.com.fis.consentcore.shared.security.RequestContextFactory;
import vn.com.fis.consentcore.reference.api.ReferenceDataApi;

@RestController
@RequestMapping("/api/v1/admin/reference/permissions")
class ReferenceDataController {
    private final ReferenceDataApi api;
    private final RequestContextFactory contextFactory;

    ReferenceDataController(ReferenceDataApi api, RequestContextFactory contextFactory) {
        this.api = api;
        this.contextFactory = contextFactory;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UUID upsert(@Valid @RequestBody PermissionRequest body, HttpServletRequest request) {
        return api.upsertPermission(new ReferenceDataApi.PermissionDefinition(
                body.permissionCode(), body.displayName(), body.description(), body.schemaVersion(),
                body.attributesSchema(), body.active()), contextFactory.create(request));
    }

    @GetMapping
    List<ReferenceDataApi.PermissionDefinition> list(HttpServletRequest request) {
        return api.listPermissions(contextFactory.tenantId(request));
    }

    record PermissionRequest(
            @NotBlank String permissionCode,
            @NotBlank String displayName,
            String description,
            @Min(1) int schemaVersion,
            @NotNull Map<String, Object> attributesSchema,
            boolean active) { }
}
