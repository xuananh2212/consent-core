package vn.com.fis.consentcore.registry.domain.model;

public enum ConsentStatus {
    REGISTERED,
    AWAITING_AUTHORIZATION,
    AUTHORIZED,
    REJECTED,
    SUSPENDED,
    REVOKED,
    EXPIRED,
    CANCELLED
}
