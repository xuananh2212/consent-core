package vn.com.fis.consentcore.shared.helper;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class UuidUtils {
    private UuidUtils() {
        //Utility class
    }

    public static byte[] toBytes(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());

        return buffer.array();
    }

    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID must contain exactly 16 bytes, but got " + bytes.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long mostSignificantBits = buffer.getLong();
        long leastSignificantBits = buffer.getLong();

        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
