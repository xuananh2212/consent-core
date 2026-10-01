package vn.com.fis.consentcore.audit.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.com.fis.consentcore.shared.api.ActorType;

public interface AuditQueryApi {
    AuditPage search(AuditCriteria criteria);

    record AuditCriteria(
            String tenantId,
            String aggregateType,
            UUID aggregateId,
            String action,
            String actorId,
            String correlationId,
            Instant occurredFrom,
            Instant occurredTo,
            int page,
            int size) {
    }

    record AuditItem(
            UUID id,
            String tenantId,
            String aggregateType,
            UUID aggregateId,
            String action,
            String result,
            String actorId,
            ActorType actorType,
            String sourceSystem,
            String correlationId,
            String requestId,
            Map<String, Object> details,
            Instant occurredAt) {
    }

    record AuditPage(List<AuditItem> items, int page, int size, long totalElements, int totalPages) {
        public AuditPage {
            items = List.copyOf(items);
        }
    }
}
