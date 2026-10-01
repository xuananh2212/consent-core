# Consent Core alignment with Consent Flow Manager v0.2

This project implements the Core-side changes from **Consent Core – Detailed Design and Implementation Specification v0.1**.

## Canonical boundary

```text
Keycloak SPI / AS Adapter
          |
          | StartFlow / GetFlowResult
          v
     Flow Manager
          |
          | /internal/v1/consents/**
          v
      Consent Core
```

Forbidden path: `Keycloak SPI -> Consent Core` for an active interaction.

## Added Core-side contract

- `GET /internal/v1/consents/{id}/authorization-context`
- `POST /internal/v1/consents/{id}/prepare-authorization`
- `GET /internal/v1/consents/{id}/presentation?revision=`
- `POST /internal/v1/consents/{id}/authorize`
- `POST /internal/v1/consents/{id}/reject`
- `GET /internal/v1/consents/{id}/authorization-validation`
- `GET /internal/v1/consents/command-results?operation=&idempotencyKey=`

The internal decision API requires a normalized `VerifiedAuthenticationContext` and `InteractionProvenance`. Raw IAM tokens are forbidden.

## Supported IAM placement

The Core contract is identical whether the Bank Mobile Backend uses:

1. `KEYCLOAK_PRIMARY`, or
2. `EXTERNAL_IAM_TRUSTED`.

Flow Manager verifies the IAM result and maps it to the canonical authentication context. Core only validates business binding and configured assurance.

## Database

V10 adds append-only `consent_authentication_evidence`, bound to `consent_decision`. V1-V9 are unchanged.

## Security

JWT mode separates Flow Manager scopes:

- `consent.flow.read`
- `consent.flow.write`

Header mode remains a local-development mode and must not be used as a production trust boundary.
