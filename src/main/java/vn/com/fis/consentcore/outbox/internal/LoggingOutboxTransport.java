package vn.com.fis.consentcore.outbox.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.com.fis.consentcore.outbox.api.OutboxMessage;
import vn.com.fis.consentcore.outbox.api.OutboxTransport;

@Configuration(proxyBeanMethods = false)
class LoggingOutboxTransport {
    private static final Logger log = LoggerFactory.getLogger(LoggingOutboxTransport.class);

    @Bean
    @ConditionalOnMissingBean(OutboxTransport.class)
    OutboxTransport loggingTransport() {
        return this::publish;
    }

    private void publish(OutboxMessage message) {
        log.info("Published outbox event id={} type={} aggregateId={} tenantId={}",
                message.id(), message.eventType(), message.aggregateId(), message.tenantId());
    }
}
