# ADR-009 — Configuration-driven Consent Data Enrichment during preparation

- Status: Accepted for reference implementation
- Date: 2026-08-15

## Context

Consent content may depend on data held by bank backends (for example account candidates, customer context, corporate authority or arbitrary bank-specific data). Calling those systems as isolated utilities would leave unclear when the data is fetched, how it is bound to the PSU decision, and which component chooses the backend.

Hard-coding `AccountInformationProvider`, `PaymentProvider`, or consent-type `if/else` branches would also make the Core difficult to reuse across banks and future consent types.

## Decision

1. Flow Manager calls the Core business operation `prepare-authorization`; it does not choose/call bank backends.
2. Core resolves active requirements from Reference & Configuration. The original `tenant + consentType + resolutionPhase` baseline is extended by ADR-011 with optional `clientId + requiredScopes` selectors; no arbitrary expression engine is introduced.
3. A requirement names a logical `dataType` and purpose (`SELECTION`, `CONTEXT`, `POLICY`).
4. `dataType` is mapped to a provider using configuration (`data_provider_binding` / `data_provider_definition` / optional mapping profile).
5. Providers implement generic `ConsentDataProvider`; account/payment values are configuration examples, not framework enums.
6. Required `PREPARE_AUTHORIZATION` data must resolve before transition to `AWAITING_AUTHORIZATION`.
7. SELECTION results are candidates only. PSU selection must be submitted, revalidated against the current candidate set, and materialized into an immutable consent revision before final presentation/decision.
8. The selection candidate set is hashed. A changed candidate set causes fail-closed stale-preparation handling and re-presentation.
9. Remote provider calls are outside the final DB write transaction. Before committing content/lifecycle changes, Core rechecks consent version/revision/hash.
10. Business provider mapping is PostgreSQL reference/configuration, not `application.yml`; secrets remain external references.

## Consequences

- New consent/data types can normally be added through configuration and a provider/mapping adapter without lifecycle changes.
- Flow Manager remains independent from bank backend topology.
- Final PSU decision remains bound to the exact immutable revision/content hash after enrichment/selection.
- Configuration governance, provider security, timeout/resilience and contract tests become part of production acceptance.
