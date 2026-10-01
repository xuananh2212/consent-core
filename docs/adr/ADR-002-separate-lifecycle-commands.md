# ADR-002: Separate lifecycle command contracts

## Status

Accepted.

## Decision

Each lifecycle operation has its own command and use-case interface. Shared metadata is carried by `CommandContext`, but preconditions and operation-specific fields remain explicit.

A generic `LifecycleConsentCommand` is forbidden.

## Rationale

Authorization, rejection, suspension, revocation, expiration and cancellation have different mandatory data, authorization policy, audit semantics and future extension points. A generic action field would move domain meaning into branching code and weaken compile-time contracts.
