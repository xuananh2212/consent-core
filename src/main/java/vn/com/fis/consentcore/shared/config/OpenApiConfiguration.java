package vn.com.fis.consentcore.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
    @Bean
    OpenAPI consentRegistryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Consent Core API")
                        .version("v1")
                        .description("Omnichannel consent registry with immutable content revisions, "
                                + "decision capture, evidence management, policy, audit and outbox."))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")));
    }

    @Bean
    OperationCustomizer localHeaderContextCustomizer(
            @Value("${consent.security.mode:header}") String securityMode) {
        return (operation, handlerMethod) -> {
            if ("header".equalsIgnoreCase(securityMode)) {
                operation.addParametersItem(header("X-Tenant-Id", true, "Trusted tenant context (local mode only)"));
                operation.addParametersItem(header("X-Actor-Id", true, "Trusted actor identity (local mode only)"));
                operation.addParametersItem(header("X-Actor-Type", false, "USER, SERVICE or SYSTEM"));
                operation.addParametersItem(header("X-Correlation-Id", false, "End-to-end correlation id"));
                operation.addParametersItem(header("X-Request-Id", false, "Unique request id"));
                operation.addParametersItem(header("X-Source-System", false, "Calling system"));
            }
            return operation;
        };
    }

    private static Parameter header(String name, boolean required, String description) {
        return new Parameter().in("header").name(name).required(required).description(description);
    }
}
