package vn.com.fis.consentcore.registry.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.com.fis.consentcore.registry.api.command.AuthorizeConsentCommand;
import vn.com.fis.consentcore.registry.api.command.PrepareAuthorizationCommand;
import vn.com.fis.consentcore.registry.api.command.RejectConsentCommand;

@Component
final class FlowAuthorizationRequestHasher {
    private final ObjectMapper mapper;

    FlowAuthorizationRequestHasher(ObjectMapper objectMapper) {
        this.mapper = objectMapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    String hash(PrepareAuthorizationCommand command) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("consentId", command.consentId());
        values.put("clientId", normalize(command.clientId()));
        values.put("scopes", command.scopes());
        values.put("subjectRef", normalize(command.subjectRef()));
        values.put("selectionContextHash", normalize(command.selectionContextHash()));
        values.put("selections", command.selections());
        return hash(values);
    }

    String hash(AuthorizeConsentCommand command) {
        Map<String, Object> values = common(command.consentId(), command.expectedVersion(),
                command.expectedRevisionNo(), command.expectedContentHash(), command.clientId(),
                command.subjectRef(), command.authenticationContext(), command.interactionProvenance(),
                command.decidedAt());
        values.put("authorizationReference", normalize(command.authorizationReference()));
        return hash(values);
    }

    String hash(RejectConsentCommand command) {
        Map<String, Object> values = common(command.consentId(), command.expectedVersion(),
                command.expectedRevisionNo(), command.expectedContentHash(), command.clientId(),
                command.subjectRef(), command.authenticationContext(), command.interactionProvenance(),
                command.decidedAt());
        values.put("reasonCode", normalize(command.reasonCode()));
        values.put("reasonDetail", normalize(command.reasonDetail()));
        return hash(values);
    }

    private Map<String, Object> common(
            Object consentId, Object expectedVersion, Object expectedRevisionNo, Object expectedContentHash,
            Object clientId, Object subjectRef, Object authenticationContext, Object interactionProvenance,
            Object decidedAt) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("consentId", consentId);
        values.put("expectedVersion", expectedVersion);
        values.put("expectedRevisionNo", expectedRevisionNo);
        values.put("expectedContentHash", expectedContentHash);
        values.put("clientId", clientId);
        values.put("subjectRef", subjectRef);
        values.put("authenticationContext", authenticationContext);
        values.put("interactionProvenance", interactionProvenance);
        values.put("decidedAt", decidedAt);
        return values;
    }

    private String hash(Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(mapper.writeValueAsBytes(value)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("Cannot calculate Flow Manager request hash", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
