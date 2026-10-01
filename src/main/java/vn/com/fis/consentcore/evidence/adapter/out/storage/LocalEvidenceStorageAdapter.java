package vn.com.fis.consentcore.evidence.adapter.out.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.com.fis.consentcore.evidence.api.EvidenceStoragePort;

@Component
@ConditionalOnProperty(name = "consent.evidence.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalEvidenceStorageAdapter implements EvidenceStoragePort {
    private final Path root;

    public LocalEvidenceStorageAdapter(
            @Value("${consent.evidence.storage.local-root:./data/evidence}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject store(
            String tenantId, UUID consentId, String originalFilename,
            String mediaType, InputStream content) {
        String safeTenant = safeSegment(tenantId);
        String objectKey = safeTenant + "/" + consentId + "/" + UUID.randomUUID();
        Path target = resolve(objectKey);
        Path temporary = target.resolveSibling(target.getFileName() + ".uploading");
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (InputStream source = new DigestInputStream(new BufferedInputStream(content), digest);
                 OutputStream output = new BufferedOutputStream(Files.newOutputStream(temporary))) {
                size = source.transferTo(output);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return new StoredObject("LOCAL_FILESYSTEM", root.toString(), objectKey,
                    size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            throw new IllegalStateException("Unable to store evidence artifact", exception);
        }
    }

    @Override
    public InputStream load(String objectKey) {
        try {
            Path path = resolve(objectKey);
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new IllegalArgumentException("Evidence object is not available");
            }
            return new BufferedInputStream(Files.newInputStream(path));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load evidence artifact", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete evidence artifact", exception);
        }
    }

    private Path resolve(String objectKey) {
        Path path = root.resolve(objectKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid evidence object key");
        }
        return path;
    }

    private static String safeSegment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalArgumentException("tenantId contains unsupported storage path characters");
        }
        return value;
    }
}
