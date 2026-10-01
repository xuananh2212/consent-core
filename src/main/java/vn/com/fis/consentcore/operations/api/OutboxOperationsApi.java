package vn.com.fis.consentcore.operations.api;

import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.shared.api.CommandContext;

public interface OutboxOperationsApi {
    Map<String, Long> summary(String tenantId);
    boolean requeue(UUID eventId, CommandContext context);
}
