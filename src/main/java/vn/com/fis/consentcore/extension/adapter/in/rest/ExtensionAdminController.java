package vn.com.fis.consentcore.extension.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.com.fis.consentcore.shared.security.RequestContextFactory;
import vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel;
import vn.com.fis.consentcore.registry.domain.model.CaptureMethod;
import vn.com.fis.consentcore.extension.api.ExtensionAdministrationApi;
import vn.com.fis.consentcore.extension.api.ExtensionPoint;

@RestController
@RequestMapping("/api/v1/admin/extensions")
public class ExtensionAdminController {
    private final ExtensionAdministrationApi administrationApi;
    private final RequestContextFactory contextFactory;

    public ExtensionAdminController(
            ExtensionAdministrationApi administrationApi, RequestContextFactory contextFactory) {
        this.administrationApi = administrationApi;
        this.contextFactory = contextFactory;
    }

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public UUID bind(@Valid @RequestBody BindingRequest body, HttpServletRequest request) {
        return administrationApi.bind(new ExtensionAdministrationApi.BindingCommand(
                body.extensionName(), body.extensionPoint(), body.consentType(), body.clientId(),
                body.acquisitionChannel(), body.captureMethod(), body.evidenceType(), body.executionOrder(), body.critical(), body.timeoutMs(),
                body.maxAttempts(), body.configuration(), contextFactory.create(request)));
    }

    public record BindingRequest(
            @NotBlank String extensionName,
            @NotNull ExtensionPoint extensionPoint,
            String consentType,
            String clientId,
            AcquisitionChannel acquisitionChannel,
            CaptureMethod captureMethod,
            String evidenceType,
            int executionOrder,
            boolean critical,
            @Min(1) int timeoutMs,
            @Min(1) int maxAttempts,
            Map<String, Object> configuration) { }
}
