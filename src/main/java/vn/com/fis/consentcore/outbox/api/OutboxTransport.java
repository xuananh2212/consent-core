package vn.com.fis.consentcore.outbox.api;

public interface OutboxTransport {
    void publish(OutboxMessage message);
}
