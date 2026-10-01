# ADR-011 — Controlled requirement selectors: tenant, consent type, client and OAuth scopes

- Status: Accepted for reference implementation
- Date: 2026-08-16

## Context

Selecting preparation requirements by `tenant + consentType` alone is not always sufficient. The same consent type can require different checks/data for a specific requesting client or for an OAuth request that contains specific scopes. Conversely, making applicability fully expression-driven would create a hard-to-audit rule engine.

## Decision

The baseline selector for `PREPARE_AUTHORIZATION` is intentionally limited to:

1. `tenantId` — mandatory, derived from trusted service context;
2. `consentType` — mandatory, loaded from the authoritative consent;
3. `clientId` — optional; `NULL/*` means all clients, otherwise it must match the consent requesting client;
4. `requiredScopes` — optional; empty means any scope, otherwise **all** configured scopes must be present in the trusted effective OAuth scope set.

No channel, subject type, API ID, arbitrary attributes, JavaScript/SpEL or free-form expression evaluator is part of the baseline.

If multiple selector variants with the same `requirementCode` are simultaneously effective, configuration is considered ambiguous and resolution fails rather than silently choosing one.

## Consequences

- Requirement selection is flexible enough for client/scope-specific banking use cases while remaining deterministic.
- Scope remains an OAuth authorization fact; it is not the same concept as `consentType` or Core business permission.
- Provider/backend selection remains a separate configuration concern after a logical data requirement has been selected.
