# Audit module

Provides the append-only `AuditWriter` public API and JDBC implementation.

Data ownership: `audit_event`.

Sensitive evidence content, tokens and raw personal documents must not be copied into audit details.
