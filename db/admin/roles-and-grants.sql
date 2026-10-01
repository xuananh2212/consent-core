-- Production-oriented role template. Run as a PostgreSQL administrator after Flyway has created the schema.
-- Replace example passwords through your secret-management process; do not commit real passwords.

-- create role consent_migrator login password '<managed-secret>';
-- create role consent_app login password '<managed-secret>';

-- grant connect on database consent_core to consent_migrator, consent_app;
-- grant usage, create on schema public to consent_migrator;
-- grant usage on schema public to consent_app;

-- Runtime privileges after migrations:
-- grant select, insert, update on all tables in schema public to consent_app;
-- grant usage, select on all sequences in schema public to consent_app;
-- revoke delete on consent_status_history, audit_event, consent_decision,
--     consent_evidence_verification, evidence_access_log from consent_app;

-- Ensure future Flyway-created objects receive runtime grants:
-- alter default privileges for role consent_migrator in schema public
--     grant select, insert, update on tables to consent_app;
-- alter default privileges for role consent_migrator in schema public
--     grant usage, select on sequences to consent_app;
