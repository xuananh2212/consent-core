# Consent Core completeness review

## Boundary

Included: Consent Registry, Content & Definition, Policy & Eligibility, Extension Management, Evidence Management, Audit & Event Publication, Reference & Configuration, and the Core-side API/persistence/storage/messaging ports and adapters.

Excluded: Flow Manager, redirect/CIBA/session/callback handling, teller/call-center UI, partner mapping applications and legacy-import orchestration.

## Design-to-code mapping

| Design block | Code/package | Database ownership | Assessment |
|---|---|---|---|
| Consent Registry | `registry` | `consent`, history, idempotency, decision | Complete reference implementation |
| Content & Definition | `content` | revision, permission, resource, constraint, obligation | Complete reference implementation |
| Policy & Eligibility | `policy` | consent-type policy, definition, evaluation, trusted source | Core orchestration complete; external providers plug into SPI |
| Extension Management | `extension` | registration, binding, execution log | Complete reference implementation |
| Evidence Management | `evidence` | upload session, bundle, artifact, verification, access log | Complete reference implementation with local storage adapter |
| Audit & Event | `audit`, `outbox` | audit and outbox | Complete reference implementation |
| Reference & Configuration | `reference` | catalogs and tenant/storage/retention profiles | Complete reference implementation |
| API/Persistence Adapters | module REST/JPA/JDBC adapters | no independent domain ownership | Present; vendor adapters remain replaceable |

## Gaps found during re-check and corrected in this refactor

- Whole-project naming was corrected from Consent Registry to Consent Core.
- Root package and boot class were changed to `vn.com.fis.consentcore` and `ConsentCoreApplication`.
- The aggregate-owning module was renamed to `registry`.
- Structured obligations were added to immutable content revisions.
- Versioned policy definitions and a public eligibility-provider SPI were added.
- Consent type, purpose, capture method, storage, retention and tenant configuration catalogs were added.
- Staged evidence upload sessions and expiry cleanup were added.
- Evidence artifact/verification events were added to the transactional outbox.
- Missing lifecycle/registration/evidence extension hooks were wired.
- Extension bindings were completed with client and capture-method criteria.
- Architecture tests were updated to the new module/package names.

## Items not implemented inside the generic repository

The remaining work is deployment or customer integration rather than generic Core behavior:

1. Enterprise storage/DMS/ECM adapter and lifecycle semantics specific to that product.
2. Real malware scanning/media-sniffing service.
3. Kafka/AMQP topics, schemas and downstream consumer contracts.
4. Bank-specific eligibility providers and downstream clients.
5. Keycloak realm/client/claim setup and mTLS/network controls.
6. Legal retention values, data classification and jurisdiction-specific evidence rules.
7. Capacity, HA/DR, backup/PITR, dashboards, alerts and production performance tests.

## Lifecycle terminology note

The high-level omnichannel document used illustrative states such as `GRANTED` and `PENDING_EVIDENCE_VERIFICATION`. The later detailed-design decision makes `AUTHORIZED` canonical and requires authorization only from `AWAITING_AUTHORIZATION`. This project keeps that detailed-design state machine and models evidence status separately, avoiding two competing state machines.
