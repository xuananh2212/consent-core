# Changelog


### Post-authentication eligibility gate and controlled selectors

- Added V12 fields `client_id` and `required_scopes` to `consent_data_requirement`; effective matching is tenant + consent type + optional client + ALL required scopes.
- `prepare-authorization` now resolves POLICY requirements before CONTEXT/SELECTION and evaluates a Core-owned business eligibility gate.
- Added structured `DENIED` prepare result (`reasonCode`, `messageKey`, `retryable`, correlation) that keeps consent `REGISTERED` and blocks final presentation.
- Added `PreparationEligibilityPolicyApi` reference implementation and policy evaluation audit records for `PREPARE_AUTHORIZATION`.
- Added integration coverage and IntelliJ HTTP demo for client/scope matching and post-authentication eligibility DENY.
- Runtime eligibility re-check at resource API enforcement time remains out of scope.
## 1.2.0-SNAPSHOT — 2026-08-14


### Configuration-driven Consent Data Enrichment

- Added generic `enrichment` module with `ConsentDataResolver` and replaceable `ConsentDataProvider` contract.
- Added V11 reference/configuration tables for data requirements, provider definitions/bindings, mapping profiles and data-minimized resolution provenance.
- Connected backend data resolution to Flow Manager `prepare-authorization`; required preparation data is resolved before final decision presentation.
- Added selection-aware preparation: `SELECTION_REQUIRED` keeps the consent `REGISTERED`; validated selection creates/finalizes the decision revision before `AWAITING_AUTHORIZATION`.
- Added candidate-set hashing/stale-selection protection so refreshed backend data cannot silently change what the PSU is authorizing.
- Added generic `CONFIGURED_MOCK` and `REST_JSON` providers; the framework is not hard-coded to account or payment consent types.
- Added admin REST configuration API and end-to-end Testcontainers coverage for configured selection enrichment.
- Preserved the project JDK and local PostgreSQL connection settings supplied by the user.

### Flow Manager v0.2 alignment

- Added dedicated Flow Manager internal API boundary.
- Added `VerifiedAuthenticationContext` and `InteractionProvenance` contracts.
- Added authorization-context, prepare-authorization, presentation, authorize/reject, authorization-validation and command-result APIs for Flow Manager.
- Added fail-closed client/subject/version/revision/content-hash/validity/evidence/auth-assurance validation.
- Added operation-scoped idempotency for prepare/authorize/reject interaction commands.
- Added Flyway V10 `consent_authentication_evidence` with append-only and tenant/reference-integrity protection.
- Added `AUTHORIZATION_ASSURANCE` policy resolution using the existing versioned `policy_definition` table.
- Added Flow Manager JWT scopes `consent.flow.read` and `consent.flow.write`.
- Added ADR-005 through ADR-008 and Flow Manager integration documentation.
- Fixed the consent decision query adapter to use the actual V2 column names (`decision_maker_id` and `authentication_context`).
- Preserved V1-V9 migrations and backward-compatible constructors for existing authorize/reject callers.
