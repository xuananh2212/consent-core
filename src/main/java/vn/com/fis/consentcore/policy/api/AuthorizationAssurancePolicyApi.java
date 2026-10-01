package vn.com.fis.consentcore.policy.api;

public interface AuthorizationAssurancePolicyApi {
    RequiredAssurance resolve(String tenantId, String consentType, String clientId);
}
