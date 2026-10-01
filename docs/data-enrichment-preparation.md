# Consent Data Enrichment and Prepare Authorization

This note describes the executable implementation of configuration-driven backend data retrieval added to Consent Core.

## 1. The orchestration entry point

Flow Manager does **not** call Account/CIF/Core Banking APIs directly and does not tell Core which backend URL to use. It calls:

```text
POST /internal/v1/consents/{consentId}/prepare-authorization
```

`prepare-authorization` is a business orchestration operation. For a `REGISTERED` consent it:

1. Loads the consent and immutable current content revision.
2. Builds the controlled preparation selector from trusted `tenant + consentType + clientId + OAuth scopes`.
3. Resolves effective `POLICY` requirements first and calls their configured backend providers.
4. Policy/Eligibility evaluates normalized POLICY facts. A business DENY returns structured `preparationStatus=DENIED`, keeps the consent `REGISTERED`, and stops before final presentation.
5. Only after ALLOW (or when no POLICY gate applies), resolves CONTEXT/SELECTION requirements through configured provider bindings.
6. Calls each selected `ConsentDataProvider` adapter outside the database write transaction.
7. If PSU selection is required, returns candidates and keeps the consent `REGISTERED`.
8. Re-resolves and validates a submitted selection against the candidate-set hash.
9. Materializes selected resources/context into final consent content according to requirement rules.
10. Creates/finalizes a new immutable content revision when content changed.
11. Calls the existing lifecycle use case to transition `REGISTERED -> AWAITING_AUTHORIZATION`.
12. Returns the authoritative revision number/content hash and `presentationReady=true`.

The final presentation endpoint is blocked while the consent is still `REGISTERED`.

## 2. Configuration chain

Runtime lookup is intentionally two-stage:

```text
tenant + consentType + phase
+ optional clientId + requiredScopes
       |
       v
consent_data_requirement
       |  "which logical data is needed?"
       v
dataType (any logical/bank-specific value)
       |
       v
data_provider_binding
       |  "which provider resolves this dataType?"
       v
data_provider_definition + optional data_mapping_profile
       |
       v
ConsentDataProvider bean -> backend
```

The framework is not limited to `ACCOUNT_ACCESS` or `PAYMENT`. Consent types and data types are configuration values. Example logical data types include `ACCOUNT_LIST`, `CUSTOMER_PROFILE`, `CORPORATE_AUTHORITY`, `CARD_LIST`, `LOAN_LIST` and bank-specific types.

Business mapping is stored in PostgreSQL and managed through the Reference & Configuration API. `application.yml` stays a technical bootstrap/configuration file.

## 3. Selection is not final consent content

For `dataPurpose=SELECTION`, backend results are candidate data only. The first prepare request can return:

```json
{
  "preparationStatus": "SELECTION_REQUIRED",
  "selectionContextHash": "...",
  "selectionRequirements": [
    {
      "requirementCode": "accounts",
      "dataType": "ACCOUNT_LIST",
      "required": true,
      "candidates": [
        {"id": "A001", "resourceType": "ACCOUNT", "label": "Current ****1234"},
        {"id": "A002", "resourceType": "ACCOUNT", "label": "Saving ****5678"}
      ]
    }
  ]
}
```

The consent remains `REGISTERED`. Flow Manager presents those candidates to the PSU. A second call uses the **same prepare idempotency key** and supplies the selection plus the returned hash:

```json
{
  "clientId": "tpp-001",
  "subjectRef": "psu-001",
  "selectionContextHash": "<hash from first response>",
  "selections": {
    "accounts": ["A001"]
  }
}
```

Core re-resolves the backend data. If the candidate set changed, the request fails with `CONSENT_PREPARATION_STALE`; the UI must re-present the current candidates. If valid, only the selected candidate(s) are materialized into the final consent revision.

## 4. Built-in provider adapters

### CONFIGURED_MOCK

A generic mock provider. Provider configuration can contain `candidates` for SELECTION requirements or `data` for CONTEXT/POLICY. It intentionally has no account-specific Java API.

### REST_JSON

A generic Java 21 `HttpClient` provider. It supports GET/POST, endpoint placeholders, optional host allow-list, request-body templates and JSON mapping profiles. Examples of endpoint placeholders are:

```text
{tenantId} {consentId} {consentType} {dataType} {subjectRef} {clientId}
```

Static `Authorization`/`Cookie` headers are rejected. `credentialReference` is modeled but a production secret/credential resolver remains deployment-specific; secrets are not stored as provider configuration.

## 5. Database objects (V11 + V12)

- `consent_data_requirement` — which logical data a tenant/consent type needs at a phase. V12 adds optional `client_id` and `required_scopes` selectors.
- `data_provider_definition` — provider/adapter deployment configuration; credential reference only.
- `data_provider_binding` — maps `dataType` to provider, optionally overridden by consent type.
- `data_mapping_profile` — versioned JSON normalization mapping.
- `consent_data_resolution_log` — hashes/timing/provenance only; no raw backend payload.

## 6. Main invariants implemented

- Required `PREPARE_AUTHORIZATION` requirements must resolve before the consent can enter `AWAITING_AUTHORIZATION`.
- Candidate backend data never becomes final consent resources automatically.
- A submitted selection must be a subset of the current candidate set.
- Candidate-set changes between display and submit are detected by `selectionContextHash` and fail closed.
- Backend calls occur outside the final database write transaction; the finalizer rechecks consent version/revision/hash before commit.
- The decision presentation is based on the finalized revision and content hash that later authorize/reject operations must bind to.


## 7. Post-authentication eligibility gate (v0.5)

Flow Manager triggers `prepare-authorization` immediately after successful PSU authentication when it moves from `SUBJECT_AUTHENTICATED` to `CONSENT_PREPARING`. Flow Manager does not know/check bank business conditions itself. Core resolves POLICY requirements first and can return `DENIED` before any final decision presentation is produced.

Requirement applicability is deliberately limited to `tenantId + consentType + optional clientId + requiredScopes`. `requiredScopes` uses ALL semantics. See `docs/post-authentication-eligibility.md` and ADR-010/ADR-011. Runtime eligibility re-check at resource-API enforcement time is not part of this implementation.
