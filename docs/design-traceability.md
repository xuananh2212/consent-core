# Design-to-code traceability — Consent Core Detailed Design v0.1

This file maps the Flow Manager v0.2-related implementation items from the master Consent Core detailed design to this source tree.

| Design item | Implementation |
|---|---|
| CC-FM-001 VerifiedAuthenticationContext | `registry/api/authz/VerifiedAuthenticationContext.java` |
| CC-FM-002 InteractionProvenance | `registry/api/authz/InteractionProvenance.java` |
| CC-FM-003 AuthorizationContext query | `ConsentAuthorizationContext`, `FlowManagerConsentService#getAuthorizationContext`, internal REST controller |
| CC-FM-004 AuthorizationValidation query | `AuthorizationValidationRequest/Result`, `FlowManagerConsentService#validateAuthorization` |
| CC-FM-010 Flow-bound authorize | extended `AuthorizeConsentCommand` + `AuthorizeConsentFromFlowUseCase` |
| CC-FM-011 Flow-bound reject | extended `RejectConsentCommand` + `RejectConsentFromFlowUseCase` |
| CC-FM-012 Fail-closed validators | `FlowManagerConsentService#validateDecisionBinding` and assurance validation |
| CC-FM-013 Uncertain-result resolution | operation-scoped idempotency + `GetConsentCommandResultUseCase` |
| CC-DB-010 V10 | `V10__flow_manager_provenance_and_authentication_context.sql` |
| CC-DB-011 Persistence adapter | `JdbcAuthenticationEvidenceAdapter` |
| CC-DB-012 Immutability/audit | V10 append-only trigger + decision/audit provenance |
| CC-SEC-020 Flow Manager service role | JWT scopes `consent.flow.read` / `consent.flow.write` |
| CC-SEC-021 Negative coverage | `FlowManagerAuthorizationIntegrationTest` + ArchitectureTest Keycloak dependency rule |
| CC-CONTRACT-022 OpenAPI exposure | Springdoc exposes the typed `/internal/v1/consents/**` controller contract; generated-client publication remains CI/CD work |
| CC-OBS-023 Audit fields | Flow-bound decision audit contains revision/hash, interaction reference, auth issuer/profile; operational dashboards remain deployment work |

## Deliberately outside Consent Core

- opaque redirect handles, cookies, flow verifier/challenge and anti-replay handle storage;
- Keycloak AuthenticationSession, Keycloak SPI implementation and CIBA `auth_req_id`;
- raw external-IAM token/assertion validation;
- Browser/App-to-App/CIBA routing and FlowPlan;
- authorization code/token issuance.

Those remain Flow Manager / Authorization Server responsibilities by design.


## Data Enrichment / preparation additions — 2026-08-15

| Design item | Implementation |
|---|---|
| Generic data requirement model | `enrichment/api/*`, `consent_data_requirement` |
| Configuration-driven provider selection | `DataEnrichmentConfigurationApi`, `JdbcDataEnrichmentConfigurationService`, `data_provider_binding` |
| Provider definition/mapping | `data_provider_definition`, `data_mapping_profile` |
| Backend orchestration | `JdbcConsentDataResolver` |
| Generic mock provider | `ConfiguredMockDataProvider` (`adapterKey=CONFIGURED_MOCK`) |
| Generic REST provider | `RestJsonConsentDataProvider` (`adapterKey=REST_JSON`) |
| Prepare selection result | `ConsentPreparationResult` |
| Selection validation/materialization | `ConsentPreparationMaterializer` |
| Final transaction/revalidation | `ConsentPreparationFinalizer` |
| Stale candidate protection | `selectionContextHash`, `PreparationStaleException` |
| Resolution provenance | `consent_data_resolution_log` |
| DB migration | `V11__consent_data_enrichment_framework.sql` |
| E2E test | `DataEnrichmentPreparationIntegrationTest` |
| Architecture decision | `ADR-009-configuration-driven-consent-data-enrichment.md` |


## Post-authentication eligibility additions — 2026-08-16

| Design item | Project mapping |
|---|---|
| Flow Manager timing: SUBJECT_AUTHENTICATED -> CONSENT_PREPARING | Internal Flow contract calls `prepare-authorization`; Core has no Flow Manager state dependency |
| Core business eligibility ownership | `PreparationEligibilityPolicyApi`, `JdbcPreparationEligibilityPolicyService`, POLICY-first branch in `FlowManagerConsentService` |
| Structured DENIED result | `ConsentPreparationResult.DENIED` + reasonCode/messageKey/retryable/correlationId |
| Controlled selectors client + scopes | V12, `DataRequirementDefinition.clientId/requiredScopes`, `getEffectiveRequirements(...)` |
| ALL scope matching | PostgreSQL JSONB containment + normalized/sorted scopes |
| Stop before final presentation | POLICY DENY returns before CONTEXT/SELECTION/materialization/RequestAuthorization |
| Resource-API dynamic eligibility | Deferred / intentionally not implemented in this release |
