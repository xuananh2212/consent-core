package vn.com.fis.consentcore.shared.persistence;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Converts domain time values to JDBC-native values at the persistence boundary.
 *
 * <p>The PostgreSQL JDBC driver does not infer a SQL type for {@link Instant}
 * when it is supplied through the generic {@code JdbcTemplate} Object-argument
 * APIs. Keeping the conversion here allows the domain and application layers to
 * continue using {@code Instant} while JDBC receives a concrete
 * {@code java.sql.Timestamp}.</p>
 */
public final class JdbcTime {

    private JdbcTime() {
        // Utility class.
    }

    public static Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
