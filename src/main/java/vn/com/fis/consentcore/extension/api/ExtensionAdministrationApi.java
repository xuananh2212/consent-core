package vn.com.fis.consentcore.extension.api;

import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel;
import vn.com.fis.consentcore.registry.domain.model.CaptureMethod;
import vn.com.fis.consentcore.shared.api.CommandContext;

public interface ExtensionAdministrationApi {
    UUID bind(BindingCommand command);

    record BindingCommand(
            String extensionName,
            ExtensionPoint extensionPoint,
            String consentType,
            String clientId,
            AcquisitionChannel acquisitionChannel,
            CaptureMethod captureMethod,
            String evidenceType,
            int executionOrder,
            boolean critical,
            int timeoutMs,
            int maxAttempts,
            Map<String, Object> configuration,
            CommandContext context) {
        public BindingCommand {
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        }
    }
}
