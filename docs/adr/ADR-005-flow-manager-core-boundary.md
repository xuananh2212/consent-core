# ADR-005 — Flow Manager is the interaction authority

## Status
Accepted — 2026-08-14

## Decision

- Keycloak SPI / Authorization Server adapters call **Consent Flow Manager only** for active interaction flows.
- They do not call Consent Core directly.
- Flow Manager calls Consent Core through `/internal/v1/consents/**` for authorization context, presentation, prepare, decision and final validation.
- Systems performing consent business operations outside an active interaction may call the authorized Consent Core business API directly.
- Consent Core remains flow-agnostic and does not store browser handles, cookies, Keycloak sessions, CIBA `auth_req_id`, deep links or Flow Manager state.

## Consequences
There is one interaction orchestration authority. Core remains reusable across Browser Redirect, App-to-App, CIBA and future channels.
