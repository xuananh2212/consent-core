package vn.com.fis.consentcore.outbox.api;

import vn.com.fis.consentcore.shared.api.CommandContext;
import vn.com.fis.consentcore.shared.domain.DomainEvent;

public interface OutboxWriter {
    void append(DomainEvent event, CommandContext context);
}
