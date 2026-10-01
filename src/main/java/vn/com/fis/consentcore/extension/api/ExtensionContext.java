package vn.com.fis.consentcore.extension.api;

import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.registry.domain.model.AcquisitionChannel;
import vn.com.fis.consentcore.registry.domain.model.CaptureMethod;
import vn.com.fis.consentcore.shared.api.CommandContext;

public record ExtensionContext(
        String tenantId,
        UUID consentId,
        String consentType,
        String clientId,
        AcquisitionChannel acquisitionChannel,
        CaptureMethod captureMethod,
        String evidenceType,
        CommandContext commandContext,
        Map<String, Object> attributes,
        Map<String, Object> configuration
) {
    public ExtensionContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
    }

    public ExtensionContext(
            String tenantId, UUID consentId, String consentType,
            AcquisitionChannel acquisitionChannel, String evidenceType,
            CommandContext commandContext, Map<String, Object> attributes,
            Map<String, Object> configuration) {
        this(tenantId, consentId, consentType, null, acquisitionChannel, null, evidenceType,
                commandContext, attributes, configuration);
    }

    public ExtensionContext withConfiguration(Map<String, Object> value) {
        return new ExtensionContext(tenantId, consentId, consentType, clientId, acquisitionChannel,
                captureMethod, evidenceType, commandContext, attributes, value);
    }
}
