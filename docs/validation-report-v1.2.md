# Validation report — Consent Core 1.2.0-SNAPSHOT

Date: 2026-08-14

## Scope validated

- Existing project extracted from `consent-core.rar`.
- Flow Manager v0.2 Core-side contract added without Keycloak dependency.
- V1-V9 Flyway migration files byte-compared with the uploaded project: **unchanged**.
- V10 added for normalized authentication/interaction provenance.
- All main Java sources compiled with Java 21 against the dependency set contained in the previously built Spring Boot artifact.
- A standalone in-memory harness exercised Flow-bound authorization, expected revision/hash validation, normalized authentication provenance, idempotent duplicate handling and authorization-validation.

## Results

```text
Main Java compilation: PASS
Java release:           21
Flow contract harness:  PASS (FLOW_MANAGER_CORE_HARNESS_OK)
V1-V9 unchanged:        PASS
Main source files:      189
Flyway migrations:      10
Test source files:      7
```

## Test added for normal Maven execution

`FlowManagerAuthorizationIntegrationTest` uses the project's existing PostgreSQL Testcontainers pattern and covers:

- register -> prepare -> authorize;
- revision/content-hash/version/client/subject binding;
- V10 authentication provenance persistence;
- duplicate authorize with same idempotency key;
- final authorization-validation;
- same idempotency key with changed payload -> conflict.

## Environment limitation

A full `mvn clean verify` was not executed in the artifact-generation environment because outbound Maven repository access and Docker are unavailable there. The project includes the integration test above so the development team should run `mvnw.cmd clean verify` in its normal IntelliJ/Docker environment before merging.
