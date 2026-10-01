# ADR-001: AUTHORIZED only from AWAITING_AUTHORIZATION

## Status

Accepted.

## Decision

`Consent.authorize()` accepts only a consent whose current state is `AWAITING_AUTHORIZATION`.

A trusted source with `EvidencePolicy.NOT_REQUIRED` may be auto-authorized during registration, but the application service must invoke:

```text
requestAuthorization(...)
authorize(...)
```

Both transitions may occur in one transaction. Direct assignment from `REGISTERED` to `AUTHORIZED` is forbidden.

## Consequences

- State history and events preserve the authorization boundary.
- Trusted-source behavior does not create a hidden alternative state machine.
- Tests can assert one canonical invariant for every channel.
- Any future bulk/import adapter must use the same application contract.
