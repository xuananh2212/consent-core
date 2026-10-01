package vn.com.fis.consentcore.evidence.api;

import java.io.IOException;
import java.io.InputStream;

public record EvidenceDownload(
        Content content,
        String originalFilename,
        String mediaType,
        long sizeBytes
) {
    @FunctionalInterface
    public interface Content {
        InputStream open() throws IOException;
    }
}
