# ADR-003: Custom PostgreSQL transactional outbox

## Status

Accepted under OPEN-DB-05.

## Decision

Domain events are serialized into `outbox_event` in the same database transaction as aggregate state, history and audit. A scheduled publisher claims rows using `FOR UPDATE SKIP LOCKED` and moves them through `PENDING`, `PROCESSING`, `PUBLISHED` or `DEAD`.

## Replaceable transport boundary

`LoggingOutboxTransport` is the default local transport used to exercise claiming, ordering, retry and status transitions without requiring a broker. A Kafka/AMQP adapter can replace it by providing an `OutboxTransport` bean; domain and application code remain unchanged.
