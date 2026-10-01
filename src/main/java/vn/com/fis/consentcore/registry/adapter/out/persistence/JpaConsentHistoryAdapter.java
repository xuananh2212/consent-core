package vn.com.fis.consentcore.registry.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.com.fis.consentcore.registry.api.result.ConsentHistoryResult;
import vn.com.fis.consentcore.registry.application.internal.port.ConsentHistoryPort;
import vn.com.fis.consentcore.registry.domain.model.ConsentTransition;
import vn.com.fis.consentcore.shared.api.CommandContext;

@Repository
class JpaConsentHistoryAdapter implements ConsentHistoryPort {
    private final SpringDataConsentHistoryRepository repository;

    JpaConsentHistoryAdapter(SpringDataConsentHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(List<ConsentTransition> transitions, CommandContext context) {
        repository.saveAll(transitions.stream()
                .map(transition -> ConsentStatusHistoryJpaEntity.from(transition, context))
                .toList());
    }

    @Override
    public List<ConsentHistoryResult> findByConsentId(String tenantId, UUID consentId) {
        return repository.findByTenantIdAndConsentIdOrderBySequenceNoAsc(tenantId, consentId)
                .stream()
                .map(entity -> new ConsentHistoryResult(
                        entity.id,
                        entity.consentId,
                        entity.tenantId,
                        entity.fromStatus,
                        entity.toStatus,
                        entity.reasonCode,
                        entity.reasonDetail,
                        entity.actorId,
                        entity.actorType,
                        entity.sourceSystem,
                        entity.correlationId,
                        entity.requestId,
                        entity.occurredAt
                ))
                .toList();
    }
}
