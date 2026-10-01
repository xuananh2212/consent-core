# ADR-008 — Read-only authorization validation before Authorization Server completion

## Status
Accepted — 2026-08-14

## Decision
Flow Manager can call Core `authorization-validation` before returning final success to the Authorization Server. The query is read-only and fails closed unless Core is still `AUTHORIZED` and client, subject, content revision/hash and stored interaction/authentication provenance match.

The browser/deep-link callback is not proof of consent authorization. Result verification remains a secure back-channel responsibility between Authorization Server/Keycloak SPI and Flow Manager; Flow Manager in turn uses Core as the business authority.
