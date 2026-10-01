# Operations module

Contains operational administration and scheduled maintenance: consent expiry, idempotency/outbox cleanup, staged-upload expiry and evidence retention processing.

It orchestrates through public APIs or owned operational tables and must not introduce consent business rules.
