package vn.com.fis.consentcore.enrichment.api;

public interface ConsentDataResolver {
    ResolvedDataContext resolve(ConsentDataResolutionRequest request);
}
