package vn.com.fis.consentcore.registry.application.internal.port;

import java.util.List;
import java.util.UUID;
import vn.com.fis.consentcore.registry.api.result.ConsentHistoryResult;
import vn.com.fis.consentcore.registry.domain.model.ConsentTransition;
import vn.com.fis.consentcore.shared.api.CommandContext;

public interface ConsentHistoryPort {
    void append(List<ConsentTransition> transitions, CommandContext context);
    List<ConsentHistoryResult> findByConsentId(String tenantId, UUID consentId);
}
