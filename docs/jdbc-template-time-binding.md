# JDBC time binding rule

## Problem

The PostgreSQL JDBC driver does not infer a SQL type when a `java.time.Instant`
is supplied through generic `JdbcTemplate` or `NamedParameterJdbcTemplate`
object parameters. At runtime this produces:

```text
Can't infer the SQL type to use for an instance of java.time.Instant
```

Spring may wrap that driver exception in `BadSqlGrammarException`, even though
the SQL text itself is valid.

## Canonical rule

- Domain, application, API, and event models continue to use `Instant`.
- At the JDBC boundary, convert every `Instant` parameter with:

```java
import static vn.com.fis.consentcore.shared.persistence.JdbcTime.toTimestamp;

jdbc.update(sql, toTimestamp(instant));
```

- `JdbcTime.toTimestamp(null)` returns `null`, so optional timestamp columns are
  supported.
- Reading remains unchanged, for example:

```java
rs.getTimestamp("created_at").toInstant();
```

## Areas audited

The fix covers direct time parameters in audit, consent content, evidence,
extension management, maintenance jobs, outbox, policy, reference
configuration, consent search, and decision capture persistence code.

No Flyway migration or database recreation is required for this change.
