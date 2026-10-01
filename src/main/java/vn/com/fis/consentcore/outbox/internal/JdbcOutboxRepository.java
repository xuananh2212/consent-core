package vn.com.fis.consentcore.outbox.internal;

import vn.com.fis.consentcore.outbox.api.OutboxMessage;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import vn.com.fis.consentcore.shared.helper.UuidUtils;

import static vn.com.fis.consentcore.shared.helper.UuidUtils.toBytes;
import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

@Repository
class JdbcOutboxRepository {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    JdbcOutboxRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    List<OutboxMessage> claimBatch(String workerId, int batchSize) {
        List<OutboxMessage> result = transactionTemplate.execute(status -> {
            /*
             * Recover PROCESSING events that have been locked
             * for more than 5 minutes.
             */
            jdbcTemplate.update("""
                    UPDATE outbox_event
                    SET status = 'PENDING',
                         locked_by = NULL,
                         locked_at = NULL,
                         available_at = CURRENT_TIMESTAMP
                    WHERE status = 'PROCESSING'
                         AND locked_at < CURRENT_TIMESTAMP - INTERVAL '5' MINUTE
                    """);

            /*
             * Select and lock candidates.
             * FOR UPDATE SKIP LOCKED
             */
            List<UUID> candidateIds = jdbcTemplate.query(
                    """
                            SELECT id
                             FROM outbox_event
                            WHERE ROWID IN (
                                SELECT rid
                                  FROM (
                                      SELECT ROWID rid
                                        FROM outbox_event
                                       WHERE status = 'PENDING'
                                         AND available_at <= CURRENT_TIMESTAMP
                                       ORDER BY sequence_no
                                       FETCH FIRST ? ROWS ONLY
                                  )
                            )
                            FOR UPDATE SKIP LOCKED
                            """,
                    ps -> ps.setInt(1, batchSize),
                    (rs, rowNum) -> toUuid(rs, "id")
            );

            if (candidateIds.isEmpty()) {
                return List.of();
            }

            /*
             * Claim selected rows.
             */
            for (UUID id : candidateIds) {

                jdbcTemplate.update(
                        """
                                UPDATE outbox_event
                                   SET status = 'PROCESSING',
                                       locked_by = ?,
                                       locked_at = CURRENT_TIMESTAMP
                                 WHERE id = ?
                                """,
                        workerId,
                        UuidUtils.toBytes(id)
                );
            }

            /*
             * Load claimed events.
             */
            String placeholders = String.join(
                    ",",
                    java.util.Collections.nCopies(
                            candidateIds.size(),
                            "?"
                    )
            );

            String sql = """
                    SELECT id,
                           tenant_id,
                           aggregate_type,
                           aggregate_id,
                           event_type,
                           event_version,
                           payload,
                           correlation_id,
                           occurred_at,
                           attempts
                      FROM outbox_event
                     WHERE id IN (%s)
                     ORDER BY sequence_no
                    """.formatted(placeholders);

            return jdbcTemplate.query(
                    sql,
                    ps -> {
                        for (int i = 0; i < candidateIds.size(); i++) {
                            ps.setBytes(
                                    i + 1,
                                    UuidUtils.toBytes(candidateIds.get(i))
                            );
                        }
                    },
                    this::map
            );
        });

        return result == null ? List.of() : result;
    }

    void markPublished(UUID id) {
        jdbcTemplate.update("""
                update outbox_event
                   set status = 'PUBLISHED', published_at = current_timestamp,
                       locked_by = null, locked_at = null, last_error = null
                 where id = ?
                """, UuidUtils.toBytes(id));
    }

    void markFailed(UUID id, int currentAttempts, int maxAttempts, String error) {
        int nextAttempts = currentAttempts + 1;
        boolean dead = nextAttempts >= maxAttempts;
        OffsetDateTime availableAt = dead
                ? OffsetDateTime.now(clock)
                : OffsetDateTime.now(clock)
                .plusSeconds(backoffSeconds(nextAttempts));

        jdbcTemplate.update("""
                        update outbox_event
                           set status = ?, attempts = ?, available_at = ?, last_error = ?,
                               locked_by = null, locked_at = null
                         where id = ?
                        """,
                dead ? "DEAD" : "PENDING",
                nextAttempts,
                availableAt,
                truncate(error, 2000),
                UuidUtils.toBytes(id)
        );
    }

    private OutboxMessage map(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxMessage(
                toUuid(rs, "id"),
                rs.getString("tenant_id"),
                rs.getString("aggregate_type"),
                toUuid(rs, "aggregate_id"),
                rs.getString("event_type"),
                rs.getInt("event_version"),
                rs.getString("payload"),
                rs.getString("correlation_id"),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getInt("attempts")
        );
    }

    private static long backoffSeconds(int attempts) {
        return Math.min(300L, 1L << Math.min(attempts, 8));
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static UUID toUuid(
            ResultSet rs,
            String column) throws SQLException {

        byte[] bytes = rs.getBytes(column);

        if (bytes == null) {
            return null;
        }

        if (bytes.length != 16) {
            throw new SQLException(
                    "Invalid RAW(16) UUID value for column " + column
            );
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        return new UUID(
                buffer.getLong(),
                buffer.getLong()
        );
    }
}
