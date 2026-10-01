# ADR-007 — Flow decisions are revision-bound, fail-closed and idempotent

## Status
Accepted — 2026-08-14

## Decision
Flow Manager decision commands must carry `expectedVersion`, `expectedRevisionNo`, `expectedContentHash`, `clientId`, `subjectRef`, normalized authentication context, interaction provenance and `Idempotency-Key`.

Core rejects a decision if client/subject/version/revision/hash/validity/evidence/assurance binding does not match current authoritative state. Same idempotency key plus same request returns the committed result; same key plus different payload is a conflict.

A caller resolving an uncertain timeout queries `command-results` and retries with the **same** key. It never creates a new mutation with a new key merely because the previous response was lost.
