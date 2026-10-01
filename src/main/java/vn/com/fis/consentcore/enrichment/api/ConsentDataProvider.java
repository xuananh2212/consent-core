package vn.com.fis.consentcore.enrichment.api;

/** Extension point implemented by built-in or bank-specific backend adapters. */
public interface ConsentDataProvider {
    String adapterKey();

    DataProviderResponse fetch(DataProviderRequest request, ProviderConfiguration configuration);
}
