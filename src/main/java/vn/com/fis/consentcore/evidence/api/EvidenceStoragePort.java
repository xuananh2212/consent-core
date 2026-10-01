package vn.com.fis.consentcore.evidence.api;

import java.io.InputStream;
import java.util.UUID;

public interface EvidenceStoragePort {
    StoredObject store(
            String tenantId, UUID consentId, String originalFilename,
            String mediaType, InputStream content);
    InputStream load(String objectKey);
    void delete(String objectKey);

    record StoredObject(
            String storageProvider,
            String storageContainer,
            String objectKey,
            long sizeBytes,
            String sha256) {
    }
}
