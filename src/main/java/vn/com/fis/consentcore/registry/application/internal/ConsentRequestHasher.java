package vn.com.fis.consentcore.registry.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.com.fis.consentcore.registry.api.command.RegisterConsentCommand;

@Component
final class ConsentRequestHasher {
    private final ObjectMapper mapper;

    ConsentRequestHasher(ObjectMapper objectMapper) {
        this.mapper = objectMapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    String hash(RegisterConsentCommand command) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("acquisitionChannel", command.acquisitionChannel());
        canonical.put("captureLocation", command.captureLocation());
        canonical.put("captureMethod", command.captureMethod());
        canonical.put("capturedAt", command.capturedAt());
        canonical.put("capturedBy", command.capturedBy());
        canonical.put("clientId", command.clientId());
        canonical.put("consentType", command.consentType());
        canonical.put("content", command.content());
        canonical.put("evidencePolicy", command.evidencePolicy());
        canonical.put("externalConsentId", command.externalConsentId());
        canonical.put("externalRequestId", command.externalRequestId());
        canonical.put("importBatchReference", command.importBatchReference());
        canonical.put("purpose", command.purpose());
        canonical.put("sourceSystem", command.sourceSystem());
        canonical.put("subjectId", command.subjectId());
        canonical.put("trustedAuthorizationReference", command.trustedAuthorizationReference());
        canonical.put("trustedSourceAuthorization", command.trustedSourceAuthorization());
        canonical.put("validFrom", command.validFrom());
        canonical.put("validUntil", command.validUntil());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(mapper.writeValueAsBytes(canonical)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("Cannot calculate registration request hash", exception);
        }
    }
}
