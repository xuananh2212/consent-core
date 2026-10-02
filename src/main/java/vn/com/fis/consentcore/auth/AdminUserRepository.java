package vn.com.fis.consentcore.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.com.fis.consentcore.shared.api.ActorType;
import vn.com.fis.consentcore.shared.persistence.JdbcTime;

@Repository
class AdminUserRepository {
    private final JdbcTemplate jdbc;

    AdminUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<AdminUser> findByUsername(String username) {
        return jdbc.query("""
                select id, username, password_hash, display_name, tenant_id, actor_id, actor_type, enabled
                  from admin_user
                 where username = ?
                """, (rs, row) -> new AdminUser(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("tenant_id"),
                rs.getString("actor_id"),
                ActorType.valueOf(rs.getString("actor_type")),
                rs.getInt("enabled") == 1), username).stream().findFirst();
    }

    void insertUser(AdminUser user, Instant createdAt) {
        jdbc.update("""
                insert into admin_user
                    (id, username, password_hash, display_name, tenant_id, actor_id, actor_type, enabled, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, user.id(), user.username(), user.passwordHash(), user.displayName(),
                user.tenantId(), user.actorId(), user.actorType().name(), user.enabled() ? 1 : 0,
                JdbcTime.toTimestamp(createdAt));
    }

    void insertRefreshToken(String id, String userId, String tokenHash, Instant expiresAt, Instant createdAt) {
        jdbc.update("""
                insert into admin_refresh_token (id, user_id, token_hash, expires_at, created_at)
                values (?, ?, ?, ?, ?)
                """, id, userId, tokenHash, JdbcTime.toTimestamp(expiresAt), JdbcTime.toTimestamp(createdAt));
    }

    Optional<RefreshSession> findActiveRefresh(String tokenHash, Instant now) {
        return jdbc.query("""
                select t.id, u.id user_id, u.username, u.password_hash, u.display_name,
                       u.tenant_id, u.actor_id, u.actor_type, u.enabled
                  from admin_refresh_token t
                  join admin_user u on u.id = t.user_id
                 where t.token_hash = ? and t.revoked_at is null and t.expires_at > ?
                """, (rs, row) -> new RefreshSession(
                rs.getString("id"),
                new AdminUser(
                        rs.getString("user_id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("display_name"),
                        rs.getString("tenant_id"),
                        rs.getString("actor_id"),
                        ActorType.valueOf(rs.getString("actor_type")),
                        rs.getInt("enabled") == 1)), tokenHash, JdbcTime.toTimestamp(now)).stream().findFirst();
    }

    void revoke(String refreshId, Instant revokedAt) {
        jdbc.update("update admin_refresh_token set revoked_at = ? where id = ? and revoked_at is null",
                JdbcTime.toTimestamp(revokedAt), refreshId);
    }

    boolean tableExists(String tableName) {
        Integer count = jdbc.queryForObject(
                "select count(*) from user_tables where table_name = ?", Integer.class, tableName);
        return count != null && count > 0;
    }

    void createTables() {
        jdbc.execute("""
                create table admin_user (
                    id             varchar2(36) primary key,
                    username       varchar2(100) not null,
                    password_hash  varchar2(200) not null,
                    display_name   varchar2(200) not null,
                    tenant_id      varchar2(100) not null,
                    actor_id       varchar2(200) not null,
                    actor_type     varchar2(30) not null,
                    enabled        number(1) default 1 not null,
                    created_at     timestamp with time zone not null,
                    constraint uk_admin_user_username unique (username)
                )
                """);
        jdbc.execute("""
                create table admin_refresh_token (
                    id          varchar2(36) primary key,
                    user_id     varchar2(36) not null,
                    token_hash  varchar2(64) not null,
                    expires_at  timestamp with time zone not null,
                    revoked_at  timestamp with time zone,
                    created_at  timestamp with time zone not null,
                    constraint uk_admin_refresh_hash unique (token_hash),
                    constraint fk_admin_refresh_user foreign key (user_id) references admin_user (id)
                )
                """);
    }

    record RefreshSession(String refreshId, AdminUser user) {
    }
}
