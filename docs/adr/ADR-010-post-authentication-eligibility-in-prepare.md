# ADR-010 — Post-authentication eligibility is evaluated inside Consent Core preparation

- Status: Accepted for reference implementation
- Date: 2026-08-16

## Context

A bank can require additional business checks after the PSU has authenticated but before the final consent content is shown. Examples include service-registration status, customer eligibility, PSU business status or corporate authority.

Putting those checks directly in Flow Manager would make the interaction service depend on bank business rules and backend topology. Treating them as standalone backend calls would also make it unclear whether a DENY occurred before or after the immutable consent decision content was prepared.

## Decision

1. Flow Manager owns **timing only**: after successful PSU authentication it transitions `SUBJECT_AUTHENTICATED -> CONSENT_PREPARING` and calls Core `prepare-authorization`.
2. Flow Manager does not choose or call the eligibility backend.
3. `PrepareAuthorizationUseCase` in Consent Core resolves `POLICY` data requirements first through the generic Data Enrichment framework.
4. Consent Core Policy/Eligibility evaluates the normalized POLICY facts.
5. `ALLOW` continues with CONTEXT/SELECTION resolution and final content preparation.
6. `DENY` returns a structured preparation result (`DENIED`, safe `reasonCode`, `messageKey`, `retryable`, correlation). Core does not create the final decision presentation and does not call `RequestAuthorization`.
7. Eligibility DENY is **not** PSU `REJECTED`; the consent remains `REGISTERED` in the reference implementation.
8. Dynamic re-check during resource API enforcement is explicitly outside this ADR and will be designed separately if required.

## Reference implementation convention

A POLICY requirement can declare an `eligibility` configuration object. Its provider/mapping adapter normalizes bank-specific backend output to a boolean field (default `eligible`). The requirement configuration owns the safe denial metadata. This deliberately avoids an arbitrary expression/rule engine in Core.

## Consequences

- Flow Manager remains generic and independent of bank business checks.
- Required eligibility checks happen before selection/content/presentation work.
- DENY behavior is deterministic and auditable through `policy_evaluation` and data-resolution provenance.
- New checks are normally added by configuration plus provider/mapping changes, not by changing the Flow Manager state machine.
