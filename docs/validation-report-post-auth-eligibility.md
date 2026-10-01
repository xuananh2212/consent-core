# Validation Report - Post-Authentication Eligibility v0.5

Validation date: 2026-08-16

## Scope

This report covers the v0.5 implementation added on top of the latest Consent Data Enrichment project:

- post-authentication business eligibility inside `PrepareAuthorizationUseCase`;
- POLICY-first backend resolution and structured `DENIED` result;
- controlled requirement selectors: tenant + consent type + optional client ID + required OAuth scopes;
- Flyway V12 additive migration;
- preservation of the user's existing Java/PostgreSQL local environment configuration.

## Results

| Check | Result | Notes |
|---|---|---|
| Compile all main Java sources with Java 21 | PASS | 214 source files compiled to 283 classes using `javac --release 21` and the runtime dependency set from the existing executable JAR. |
| Generic CONFIGURED_MOCK provider smoke | PASS | Returned normalized selection candidate successfully. |
| Generic REST_JSON provider smoke | PASS | Called a local HTTP test backend and normalized the JSON candidate successfully. |
| Preparation eligibility policy smoke | PASS | Normalized `eligible=false` produced structured DENY with `PSU_SERVICE_NOT_REGISTERED` and wrote one policy-evaluation record through a capturing JdbcTemplate. |
| Fat JAR refresh | PASS | Updated JAR contains V12 and the new PreparationEligibilityPolicy classes. |
| Preserve `pom.xml` | PASS | SHA-256 unchanged from supplied/latest baseline: `dd13a3d5f8d5eb77503731852ee645c389ae3f3ec13302886f836a0758b78721`. |
| Preserve `application.yml` | PASS | SHA-256 unchanged from supplied/latest baseline: `f2839f56eb36a3cf784aac182c10450fe489ae6892fe886840c8afbc220c1871`; default DB URL remains `jdbc:postgresql://localhost:5433/consent_core`. |
| Full Maven `clean verify` | NOT RUN HERE | The environment has no installed Maven and Maven Wrapper cannot download Maven because outbound DNS/network access is unavailable. This is an environment limitation, not a compile failure. |
| Testcontainers/PostgreSQL integration suite | NOT RUN HERE | Docker is not installed in this execution environment. Run `mvnw.cmd clean verify` on the target development machine. |

## Source-level integration coverage added

`PostAuthenticationEligibilityIntegrationTest` covers:

1. eligibility DENY before selection/context resolution, keeping consent `REGISTERED` and presentation unavailable;
2. eligibility ALLOW continuing preparation to `AWAITING_AUTHORIZATION`;
3. optional `clientId` selector;
4. `requiredScopes` ALL/subset semantics.

## Recommended local verification

On the target Windows development machine:

```bat
mvnw.cmd clean verify
```

Then run `ConsentCoreApplication` against the existing local PostgreSQL instance and execute `http/post-auth-eligibility.http` from IntelliJ HTTP Client.
