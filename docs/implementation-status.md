# Consent Core implementation status

## Scope statement

This repository implements the **Consent Core modular monolith**. Consent Registry & Lifecycle is the aggregate-owning module, not the name of the whole application. Flow Manager and Channel/Acquisition Adapters are outside the repository.

## Core capability status

| Consent Core capability | Status | Main implementation |
|---|---|---|
| Consent Registry & Lifecycle | Implemented | `registry` |
| Content & Definition | Implemented | `content` |
| Permission/resource/constraint/obligation | Implemented | content API + V2/V9 |
| Immutable revision and presentation snapshot | Implemented | content service + DB guards |
| Acquisition context and decision capture | Implemented | registry + `consent_decision` |
| Policy & Eligibility | Implemented reference | policy rules, definitions, trusted sources, `EligibilityProvider` SPI |
| Extension Management | Implemented | client/channel/capture/evidence/phase bindings, retry/timeout/log |
| Evidence Management | Implemented reference | staged upload, artifact, verification, custody, legal hold, retention |
| Audit & Event Publication | Implemented | append-only audit + versioned outbox + evidence/registry events |
| Reference & Configuration | Implemented | type, purpose, permission, capture, storage, retention, tenant config |
| Consent Data Enrichment | Implemented reference | generic requirements/resolver/provider registry, mock + REST JSON adapters, V11/V12 configuration/provenance |
| Selection-aware consent preparation | Implemented reference | `prepare-authorization` -> eligibility gate -> resolve candidates -> validate PSU selection -> finalize revision -> `AWAITING_AUTHORIZATION` |
| Post-authentication eligibility gate | Implemented reference | POLICY requirements resolved first; `ALLOW` continues, structured `DENIED` keeps `REGISTERED` and blocks final presentation |
| Controlled requirement selectors | Implemented | tenant + consentType + optional clientId + requiredScopes (ALL semantics), V12 |
| API & Persistence Adapters | Implemented reference | REST, JPA/JDBC, local storage, logging transport |
| Tenant isolation | Implemented in contracts and queries | tenant context in API, DB queries, events and storage paths |
| Background operations | Implemented | expiry, idempotency/outbox cleanup, evidence retention/upload expiry |
| Header and OAuth2/JWT security modes | Implemented | shared security configuration |

## Canonical lifecycle

The executable lifecycle follows the detailed-design decision set:

```text
REGISTERED -> AWAITING_AUTHORIZATION -> AUTHORIZED
```

The seven operation contracts are request authorization, authorize, reject, suspend, revoke, expire and cancel. Older high-level labels such as `GRANTED` are not added as duplicate runtime states; `AUTHORIZED` is canonical for this implementation.

## Deployment-specific implementations still required

These contracts intentionally do not select a vendor:

- `EvidenceStoragePort` for S3-compatible storage/DMS/ECM;
- `EvidenceMalwareScanner` for antivirus/malware scanning;
- `OutboxTransport` for Kafka/AMQP;
- `EligibilityProvider` for Core Banking/CRM/AML/customer eligibility sources;
- `ConsentExtension` beans for customer-specific rules;
- Keycloak realm/client/claims and service authorization configuration.

They are adapter implementations and environment configuration, not missing Consent Core modules.

## Flow Manager v0.2 alignment (2026-08-14)

| Capability | Status | Main implementation |
|---|---|---|
| Flow Manager internal Core contract | Implemented reference | `FlowManagerConsentController`, `FlowManagerConsentService` |
| Authorization context / routing facts | Implemented | `ConsentAuthorizationContext` |
| Normalized authentication context | Implemented | `VerifiedAuthenticationContext` |
| Interaction / AS provenance | Implemented | `InteractionProvenance`, Flyway V10 |
| Flow decision binding | Implemented | version + revision + content hash + client + subject validation |
| Flow decision idempotency | Implemented | operation-scoped idempotency and command-result query |
| Final Core authorization validation | Implemented | read-only fail-closed validation endpoint |
| Flow Manager JWT scopes | Implemented | `consent.flow.read`, `consent.flow.write` |
| Direct Keycloak SPI -> Core integration | Forbidden by architecture | no Keycloak dependency in project |

Authentication token/assertion verification remains a Flow Manager responsibility. Consent Core receives only normalized, already-verified authentication provenance and applies business binding/assurance checks.

## Consent Data Enrichment alignment (2026-08-15)

| Capability | Status | Main implementation |
|---|---|---|
| Configuration `tenant + consentType + phase + optional client/scopes -> dataType` | Implemented | `consent_data_requirement`, `DataEnrichmentConfigurationApi`, V12 |
| Configuration `dataType -> provider` | Implemented | `data_provider_binding`, `data_provider_definition` |
| Versioned normalization mapping | Implemented reference | `data_mapping_profile`, `REST_JSON` provider |
| Generic provider SPI | Implemented | `ConsentDataProvider` |
| Generic configured mock | Implemented | `ConfiguredMockDataProvider` |
| Generic REST/JSON backend call | Implemented reference | `RestJsonConsentDataProvider` |
| Prepare orchestration | Implemented | `FlowManagerConsentService`, `ConsentPreparationMaterializer`, `ConsentPreparationFinalizer` |
| Selection candidate/stale protection | Implemented | `selectionContextHash`, selection validation, `CONSENT_PREPARATION_STALE` |
| Data-minimized resolution provenance | Implemented | `consent_data_resolution_log` |
| Production credential resolver / enterprise resilience stack | Deployment-specific | secret manager/mTLS/OAuth2, advanced circuit breaker/bulkhead policies |

`ACCOUNT_LIST`/`ACCOUNT_INFORMATION` are demo configuration values only. The Java framework does not constrain the catalog to account/payment consent. Post-authentication eligibility uses the same generic framework: Flow Manager triggers prepare after authentication, while Core chooses/evaluates POLICY requirements. Dynamic eligibility re-check during resource API enforcement is intentionally deferred.
