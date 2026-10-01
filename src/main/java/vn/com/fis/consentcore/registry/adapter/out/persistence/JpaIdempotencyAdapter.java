package vn.com.fis.consentcore.registry.adapter.out.persistence;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.Optional;

import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.com.fis.consentcore.registry.application.internal.port.IdempotencyPort;
import vn.com.fis.consentcore.registry.application.internal.port.IdempotencyRecord;

@Repository
class JpaIdempotencyAdapter implements IdempotencyPort {
    private final SpringDataConsentIdempotencyRepository repository;
    private final JdbcTemplate jdbc;

    JpaIdempotencyAdapter(
            SpringDataConsentIdempotencyRepository repository,
            JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Override
    public void acquireTransactionLock(String tenantId, String idempotencyKey, String operation) {
        String lockName = tenantId + '|' + operation + '|' + idempotencyKey;

        CallableStatementCreator creator = connection -> {
            CallableStatement cs = connection.prepareCall(
                    "BEGIN " +
                        "  ? := DBMS_LOCK.REQUEST(" +
                        "      id => DBMS_UTILITY.GET_HASH_VALUE(?, 0, 1073741823)," +
                        "      lockmode => DBMS_LOCK.X_MODE," +
                        "      timeout => ?," +
                        "      release_on_commit => TRUE" +
                        "  ); " +
                        "END;"
            );

            cs.registerOutParameter(1, Types.INTEGER);
            cs.setString(2, lockName);
            cs.setInt(3, 10); //wait 10s

            return cs;
        };

        CallableStatementCallback<Void> callback = cs -> {
            cs.execute();

            int result = cs.getInt(1);

            if (result != 0) {
                throw new IllegalStateException(
                        "Failed to acquire DBMS_LOCK, result=" + result);
            }

            return null;
        };

        jdbc.execute(creator, callback);
    }

    @Override
    public Optional<IdempotencyRecord> find(String tenantId, String idempotencyKey, String operation) {
        return repository.findByTenantIdAndIdempotencyKeyAndOperation(tenantId, idempotencyKey, operation)
                .map(ConsentIdempotencyJpaEntity::toRecord);
    }

    @Override
    public void save(IdempotencyRecord record) {
        repository.save(ConsentIdempotencyJpaEntity.from(record));
    }
}
