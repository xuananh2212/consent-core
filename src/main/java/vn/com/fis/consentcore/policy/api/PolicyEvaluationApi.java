package vn.com.fis.consentcore.policy.api;

public interface PolicyEvaluationApi {
    RegistrationPolicyDecision evaluateRegistration(RegistrationPolicyRequest request);
}
