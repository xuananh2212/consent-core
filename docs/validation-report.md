# Validation report — Consent Core refactor

## Performed

- Project identity refactored to `consent-core`, `vn.com.fis.consentcore` and `ConsentCoreApplication`.
- Registry module renamed to `registry`; old package/name references scanned.
- XML and JSON configuration parsed.
- Flyway sequence checked as V1 through V9; previously released V1–V8 files retained.
- Java 21 static compilation completed for domain, public APIs and the modified Registry, Content, Policy, Evidence, Reference and Extension application services using framework API stubs.
- INV-02 implementation and separate lifecycle command contracts retained.
- Architecture test package rules updated for the Registry module.

## Environment limitation

The packaging environment could not resolve the Apache Maven download hosts, so it could not execute the real Maven/Spring Boot/Testcontainers build. Run this in IntelliJ on a machine with the dependencies and Docker available:

```bat
mvnw.cmd clean verify
```

A successful user-side Maven build is the final validation for Spring wiring, Flyway execution and Testcontainers behavior.
