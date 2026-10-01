# ADR-006 — Store normalized authentication provenance, never raw IAM tokens

## Status
Accepted — 2026-08-14

## Decision
Flow Manager verifies the actual IAM assertion/token/attestation and sends a `VerifiedAuthenticationContext` to Core. Core persists normalized provenance in `consent_authentication_evidence` (Flyway V10), bound to the immutable consent decision.

Core stores issuer, subject, authentication time, ACR, AMR, evidence reference/hash, verification profile and interaction/AS references. It does **not** store raw IAM JWTs, passwords, OTPs, biometric material, Flow handles or verifier secrets.

## Consequences
Both `KEYCLOAK_PRIMARY` and `EXTERNAL_IAM_TRUSTED` are supported without a Keycloak or vendor IAM dependency in Consent Core.
