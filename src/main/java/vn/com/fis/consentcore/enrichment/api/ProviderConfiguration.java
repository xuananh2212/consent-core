package vn.com.fis.consentcore.enrichment.api;

import java.util.Map;

/** Effective provider + mapping configuration. Credentials remain references, not secrets. */
public record ProviderConfiguration(
        String providerCode,
        String providerType,
        String adapterKey,
        String endpointTemplate,
        String httpMethod,
        String credentialReference,
        int timeoutMs,
        Map<String, Object> providerConfiguration,
        Map<String, Object> bindingConfiguration,
        Map<String, Object> mappingConfiguration
) {
    public ProviderConfiguration {
        providerConfiguration = providerConfiguration == null ? Map.of() : Map.copyOf(providerConfiguration);
        bindingConfiguration = bindingConfiguration == null ? Map.of() : Map.copyOf(bindingConfiguration);
        mappingConfiguration = mappingConfiguration == null ? Map.of() : Map.copyOf(mappingConfiguration);
    }
}
