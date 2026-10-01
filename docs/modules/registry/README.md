# Consent Registry module

Owns the Consent aggregate, acquisition context, decision/evidence references, lifecycle invariants, idempotency and search.

Public contracts: `vn.com.fis.consentcore.registry.api`.

Data ownership: `consent`, `consent_status_history`, `consent_idempotency`, `consent_decision`.

Canonical rule: `AUTHORIZED` only from `AWAITING_AUTHORIZATION`; no caller may set arbitrary status.
