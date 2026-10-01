# Validation report — configuration-driven Consent Data Enrichment

Date: 2026-08-15

## Validation performed in this update

- Extracted and used the user-supplied `consent-core-v1.2.0` project as the baseline.
- Preserved the supplied environment-sensitive configuration files byte-for-byte:
  - `pom.xml` (Java 21 / Maven enforcer range as supplied)
  - `src/main/resources/application.yml` (local PostgreSQL URL keeps port `5433`)
  - `src/main/resources/application-prod.yml`
  - `compose.yaml`
  - `.idea/misc.xml`
- Compiled **all `src/main/java` sources** with Java 21 against the dependency JARs already packaged in the existing Spring Boot fat JAR. Compilation succeeded.
- Ran an isolated smoke harness against `REST_JSON` using a local HTTP server. Endpoint-template, host allow-list, JSON-path mapping and candidate normalization succeeded.
- Ran an isolated smoke harness against `CONFIGURED_MOCK`. Generic candidate normalization succeeded.
- Rebuilt `target/classes` from the current sources/resources and overlaid the current classes/resources into the existing executable Spring Boot fat JAR; verified that V11 and new enrichment classes are present.
- Ran `git diff --check`; no whitespace errors were reported.

## Automated test added

`DataEnrichmentPreparationIntegrationTest` exercises the intended PostgreSQL/Testcontainers sequence:

1. configure a generic consent type;
2. configure `CONFIGURED_MOCK` provider;
3. bind a logical data type to the provider;
4. declare a `PREPARE_AUTHORIZATION` / `SELECTION` requirement;
5. register consent;
6. first prepare returns `SELECTION_REQUIRED` and leaves consent `REGISTERED`;
7. second prepare submits `selectionContextHash` + PSU selection;
8. Core finalizes revision 2 and moves to `AWAITING_AUTHORIZATION`;
9. final content contains only the selected resource;
10. duplicate finalized prepare is idempotent.

## Environment limitation of this validation run

A complete `mvn verify` / Testcontainers run could not be executed in the build sandbox because a Maven distribution/test dependency cache and a PostgreSQL/Docker runtime were not available there. The project therefore still needs the normal local quality gate on the developer machine:

```bat
mvnw.cmd clean verify
```

and then the executable HTTP scenario:

```text
http/data-enrichment-preparation.http
```

This limitation does not change the source/config preservation checks or the successful full main-source Java 21 compilation above.
