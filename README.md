# DBA
Grant for core_admin: GRANT EXECUTE ON SYS.DBMS_LOCK TO CORE_ADMIN;

# Consent Core

Executable reference implementation of an omnichannel **Consent Core** using Java 21, Spring Boot 3.4.5, Maven single-module and PostgreSQL.

The application is a tightly modularized monolith. **Consent Registry & Lifecycle is one module inside the application**, together with Content & Definition, Policy & Eligibility, Extension Management, Evidence Management, Audit & Event Publication, Reference & Configuration, and API/Persistence adapters.

Flow Manager and channel/acquisition applications are deliberately outside this repository. They never write Consent Core tables directly. For active digital interactions, Keycloak/Authorization Server adapters call Flow Manager only; Flow Manager uses the dedicated `/internal/v1/consents/**` Core contract.

## Project identity

```text
Maven artifact : vn.com.fis:consent-core
Root package   : vn.com.fis.consentcore
Boot class     : ConsentCoreApplication
Database       : consent_core
HTTP collection: http/consent-core.http
```

The legacy Flyway filename `V1__consent_registry_baseline.sql` is intentionally retained because released migrations must not be renamed or rewritten.

## Implemented design decisions

- Java 21, Spring Boot 3.4.5 and Maven single-module.
- Package conventions, public module APIs and ArchUnit checks preserve modular-monolith boundaries.
- PostgreSQL schema is managed by Flyway; Hibernate uses `ddl-auto=validate`.
- Direct JDBC persistence converts domain `Instant` values through `JdbcTime` before binding to PostgreSQL; see `docs/jdbc-template-time-binding.md`.
- Custom PostgreSQL transactional outbox with event version, retry, `FOR UPDATE SKIP LOCKED` and DEAD handling.
- Every lifecycle operation has its own command contract; there is no `LifecycleConsentCommand`.
- **INV-02:** `AUTHORIZED` can be reached only from `AWAITING_AUTHORIZATION`.
- Trusted-source registration still records:

  ```text
  REGISTERED -> AWAITING_AUTHORIZATION -> AUTHORIZED
  ```

- Structured content revisions include permission, resource, constraint, obligation and presentation snapshot.
- Finalized revisions are immutable and SHA-256-addressed.
- Decision capture references the exact revision.
- Evidence-required consent cannot be authorized until evidence is verified.
- Artifact bytes stay outside PostgreSQL; metadata, hash, custody, verification and retention remain in PostgreSQL.
- Evidence upload supports staged sessions so database/object-storage work is not modeled as a distributed transaction.
- Policy supports tenant consent-type rules, trusted sources, versioned policy definitions and pluggable eligibility providers.
- Extensions can be bound by tenant, client, consent type, channel, capture method, evidence type and lifecycle point.
- Tenant-owned reference/configuration includes consent types, purposes, permission catalog, capture methods, storage profiles, retention profiles and tenant defaults.
- **Configuration-driven Consent Data Enrichment** lets `prepare-authorization` resolve required backend data before the final PSU decision presentation. Requirements are configured by tenant + consent type + phase, with optional client ID and required OAuth scopes, and map logical data types to replaceable providers; Flow Manager never selects or calls a bank backend directly.
- Selection data is treated as candidate data only. A PSU selection is revalidated against the current candidate-set hash, materialized into a new immutable consent revision, and only then can the consent enter `AWAITING_AUTHORIZATION`.

## Logical modules

```text
vn.com.fis.consentcore
├── registry      Consent aggregate, lifecycle, acquisition context, decisions and search
├── content       Revision, permission, resource, constraint, obligation and presentation
├── policy        Policy definitions, eligibility SPI, consent-type rules and trusted sources
├── extension     Contract hooks, bindings, timeout/retry and execution log
├── evidence      Upload session, bundle, artifact, verification, custody and retention
├── audit         Append-only business audit and audit queries
├── outbox        Versioned transactional events and publisher operations
├── reference     Tenant reference data and configuration profiles
├── enrichment    Data requirements, provider resolution and backend normalization
├── operations    Expiry, cleanup and operational administration
└── shared        Small shared contracts, security, errors and boot configuration
```

Module notes are under `docs/modules`. The comparison with the Consent Core design is in `docs/consent-core-completeness-review.md`.

## Open and run in IntelliJ IDEA

1. Extract the ZIP.
2. Choose **File → Open** and select the folder directly containing `pom.xml`.
3. Set Project SDK and Maven importer JDK to Java 21.
4. Reload Maven and wait for dependency indexing.
5. Start PostgreSQL:

   ```bat
   docker compose up -d
   ```

6. Run `ConsentCoreApplication`, or:

   ```bat
   mvnw.cmd spring-boot:run
   ```

Flyway creates and upgrades all application tables during startup. Do not create the tables manually.

## Build and tests

Windows:

```bat
mvnw.cmd clean verify
```

Linux/macOS:

```bash
./mvnw clean verify
```

The suite includes domain invariant tests, application tests, ArchUnit rules and PostgreSQL Testcontainers scenarios for trusted authorization, immutable content/obligations, staged evidence upload, verification and authorization.

## API smoke test

Open `http/consent-core.http`, select the `local` environment from `http/client.env.json`, then run requests in order.

Local endpoints:

- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI: `http://localhost:8081/v3/api-docs`
- Health: `http://localhost:8081/actuator/health`
- Consent APIs: `http://localhost:8081/api/v1/consents`
- Reference/configuration: `http://localhost:8081/api/v1/admin/reference`

## Security modes

Local development defaults to trusted header mode. JWT/Keycloak integration is available through Spring Security OAuth2 Resource Server.

```text
consent.read
consent.write
consent.admin
consent.flow.read
consent.flow.write
```

Activate a production-like profile with:

```bat
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=prod
```

A real deployment must supply issuer, claim mappings, realm/client configuration and service authorization rules.

## Flyway migrations

```text
V1  Registry lifecycle, history, idempotency, audit and outbox baseline
V2  Content revision and decision capture
V3  Evidence bundle, artifact, verification and custody
V4  Consent-type policy, trusted source, extension and permission catalog
V5  Operational indexes
V6  Append-only, immutability and reference-integrity guards
V7  Evidence retention metadata
V8  Concurrency, uniqueness and query hardening
V9  Consent Core completeness: obligations, policy definitions, staged upload,
    reference/configuration profiles and richer extension binding
V10 Flow Manager authentication + interaction provenance bound to consent decision
V11 Generic Consent Data Enrichment configuration, provider binding and resolution provenance
V12 Post-authentication eligibility selectors (`client_id`, `required_scopes`)
```

Never edit or rename a migration that has been applied to a shared environment. Add V11, V12 and later migrations.

## Configuration-driven consent preparation and backend data

For active digital consent, Flow Manager calls one business operation:

```text
POST /internal/v1/consents/{consentId}/prepare-authorization
```

Consent Core then performs the orchestration internally:

```text
consent tenant + consentType
        -> active PREPARE_AUTHORIZATION data requirements
        -> logical dataType(s)
        -> provider binding / provider definition / mapping profile
        -> ConsentDataProvider adapter
        -> bank backend
        -> normalized selection/context/policy data
        -> final consent content + immutable revision/hash
        -> AWAITING_AUTHORIZATION
```

The mapping is stored in PostgreSQL reference/configuration tables (`consent_data_requirement`, `data_provider_binding`, `data_provider_definition`, `data_mapping_profile`). It is not hard-coded to ACCOUNT/PAYMENT and is not kept in `application.yml`. `ACCOUNT_LIST` is only a demo data type; deployments can add arbitrary types such as `CUSTOMER_PROFILE`, `CORPORATE_AUTHORITY`, `CARD_LIST` or bank-specific types without changing the lifecycle.

Built-in reference providers:

- `CONFIGURED_MOCK`: generic configuration-backed provider for local/test scenarios.
- `REST_JSON`: generic JSON REST provider with endpoint templates, host allow-list support and mapping profiles. Production credential lookup is intentionally left behind a deployment-specific secret/credential adapter rather than storing secrets in database configuration.

When a requirement has purpose `SELECTION`, the first prepare call can return `SELECTION_REQUIRED` while the consent remains `REGISTERED`. Flow Manager presents the candidate model, then submits the PSU selection together with `selectionContextHash`. Core re-resolves/revalidates the candidate set, materializes only valid selections into consent resources, finalizes the decision revision and returns `PREPARED`. See `docs/data-enrichment-preparation.md` and `http/data-enrichment-preparation.http`.

## Post-authentication business eligibility

After the PSU has been authenticated, Flow Manager enters its consent-preparation stage and still calls only the same Core operation:

```text
POST /internal/v1/consents/{consentId}/prepare-authorization
```

Consent Core resolves effective `POLICY` data requirements first, selected by `tenantId + consentType + optional clientId + requiredScopes`. If a configured eligibility gate evaluates to DENY, Core returns a structured `DENIED` preparation result and keeps the consent `REGISTERED`; no final decision presentation is exposed. Only ALLOW/no-gate proceeds to CONTEXT/SELECTION resolution and final consent preparation.

The baseline deliberately does **not** implement a general-purpose expression engine and does **not** add dynamic eligibility re-check to resource-API enforcement. Those concerns are outside the current scope. See `docs/post-authentication-eligibility.md` and `http/post-auth-eligibility.http`.

## Flow Manager v0.2 integration

The Core remains flow-agnostic. The new internal contract supports Browser Redirect, App-to-App and CIBA without storing Flow Manager state in the Consent aggregate. Decision calls are bound to client, subject, optimistic-lock version, immutable content revision/hash and a normalized authentication context. See `docs/flow-manager-v0.2-core-alignment.md` and ADR-005 through ADR-008.

## Production adapters

The Core contracts are present, while vendor/customer implementations remain deployment choices:

- enterprise Object Storage, DMS or ECM implementation of `EvidenceStoragePort`;
- real malware/AV scanner implementation;
- Kafka/AMQP implementation of `OutboxTransport`;
- organization-specific implementations of `EligibilityProvider`;
- downstream consumers/adapters for Core Banking, CRM, AML and IAM;
- customer extension beans implementing `ConsentExtension`.

Those are not Flow Manager or channel code and do not belong in the Core domain. They are adapters plugged into the Core at deployment time.

The repository is an executable reference implementation, not a production certification. Production acceptance still requires security, performance, backup/PITR, HA/DR, legal retention and operational testing.
# consent-core
# consent-core
