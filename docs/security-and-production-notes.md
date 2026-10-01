# Security and production notes

## Production profile

Activate `prod` in protected environments. The profile enforces JWT identity, permission-catalog validation and clean malware-scan verification. It intentionally requires deployment-provided issuer and storage settings rather than local insecure defaults.

## Identity

Use `consent.security.mode=jwt` in protected environments. Tenant, subject and calling client must be derived from a verified token or a trusted gateway, never from arbitrary Internet headers.

## Authorization

JWT mode requires:

- `consent.read` for read APIs;
- `consent.write` for lifecycle/content/evidence changes;
- `consent.admin` for policy, reference, audit, extension and outbox administration.

Deployment policy should further constrain who may authorize, verify evidence, place legal hold, revoke or download artifacts.

## Sensitive data

Do not log artifact bytes, tokens, passwords, full personal records or private keys. Audit stores business references and hashes, not evidence content.

## Evidence

The local file adapter is for development/reference use. Production storage should enforce encryption, bucket/container isolation, object immutability or versioning, retention, malware scanning and access logging.

## Events

Outbox event delivery is at-least-once. Consumers must be idempotent using event identity. Use schema/version compatibility rules before connecting Kafka or another broker.

## Operations

Alert on:

- oldest pending outbox age;
- number of DEAD events;
- evidence scan failures;
- retention deletion failures;
- database pool saturation;
- optimistic-lock conflicts;
- authorization and tenant-access denials.

## Flow Manager service boundary

For production JWT mode, the Flow Manager service identity should receive only `consent.flow.read` and/or `consent.flow.write` as required. Keycloak SPI/Authorization Server adapters must not be granted a direct Core interaction path; they communicate with Flow Manager, which invokes the internal Core contract.

The application-level JWT scope checks complement, but do not replace, mTLS/workload identity and network policy between Flow Manager and Consent Core. Raw external-IAM tokens and Flow Manager browser/deep-link security artifacts are not accepted by Core contracts.
