# Consent Core database schema

Flyway owns schema creation and upgrade. Hibernate uses `ddl-auto=validate` and never creates or mutates production tables.

## Migration sequence

- V1: Registry lifecycle, status history, idempotency, audit and outbox.
- V2: Immutable content revision and decision capture.
- V3: Evidence bundle, artifact, verification and access custody.
- V4: Consent-type policy, trusted source, extensions and permission catalog.
- V5: Operational indexes.
- V6: Append-only, immutability and reference-integrity triggers.
- V7: Evidence retention fields.
- V8: Concurrency, uniqueness and query hardening.
- V9: Consent Core completeness additions.

V9 adds:

```text
consent_obligation
consent_type_catalog
purpose_definition
capture_method_catalog
storage_profile
retention_profile
tenant_configuration
policy_definition
evidence_upload_session
```

It also enriches `extension_binding` with `client_id` and `capture_method`, and adds configuration schema/version fields to extension registration.

## Application tables

### Registry and lifecycle

`consent`, `consent_status_history`, `consent_idempotency`, `consent_decision`

### Content and definition

`consent_revision`, `consent_permission`, `consent_resource`, `consent_constraint`, `consent_obligation`

### Evidence and custody

`evidence_upload_session`, `consent_evidence_bundle`, `consent_evidence_artifact`, `consent_evidence_verification`, `evidence_access_log`

### Policy and eligibility

`consent_type_policy`, `policy_definition`, `policy_evaluation`, `trusted_consent_source`

### Extension

`extension_registration`, `extension_binding`, `extension_execution_log`

### Reference and configuration

`consent_type_catalog`, `purpose_definition`, `permission_catalog`, `capture_method_catalog`, `storage_profile`, `retention_profile`, `tenant_configuration`

### Audit and publication

`audit_event`, `outbox_event`

Flyway also creates `flyway_schema_history`.

## Creation flow

```text
docker compose up -d
        -> run ConsentCoreApplication
        -> Flyway applies V1 ... V9
        -> Hibernate validates mappings
```

Do not manually create individual tables. In shared environments, use a migration role with DDL rights and a separate runtime role with least-privilege DML rights.

Never modify or rename an applied migration. The old V1 filename contains `consent_registry` because it is an immutable migration artifact, not because the application is still named Consent Registry.

## V10 — Flow Manager authentication provenance

`V10__flow_manager_provenance_and_authentication_context.sql` adds `consent_authentication_evidence` as an append-only child of `consent_decision`.

The table stores normalized subject/IAM assurance metadata plus Flow Manager/Authorization Server correlation references. It deliberately does not store raw IAM JWTs, browser handles, cookies, PKCE-like flow verifiers, passwords, OTPs or biometric material.

Indexes support decision lookup, interaction correlation and Authorization Server reconciliation. A DB trigger validates tenant/consent/decision ownership on insert, and the existing append-only guard prevents update/delete.


## V12 — Post-authentication eligibility requirement selectors

Migration: `V12__post_auth_eligibility_and_requirement_selectors.sql`

`consent_data_requirement` is extended with:

- `client_id varchar(200) null`: NULL means all requesting clients;
- `required_scopes jsonb not null default []`: empty means scope-independent; otherwise every configured scope must be present in the effective trusted OAuth scopes.

The old V11 uniqueness constraint is replaced with selector-aware uniqueness. Effective configuration rejects ambiguous simultaneous matches for the same `requirementCode`. V11 is not modified.
