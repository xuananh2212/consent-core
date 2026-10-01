# ADR-004: Maven single-module with enforced logical boundaries

## Status

Accepted.

## Decision

The project remains one Maven module for build and transaction simplicity. Logical modules are represented by package conventions:

- `<module>.api` is public;
- `<module>.internal` is implementation detail;
- Consent domain and adapters remain inside the Consent module;
- ArchUnit prevents reverse dependencies and cross-adapter access.

`ConsentStatus` remains in `consent.domain.model`; package placement is not used to conceal a dependency cycle.
