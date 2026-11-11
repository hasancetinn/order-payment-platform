# ADR 0002: Transactional outbox and idempotent consumers

**Status:** accepted

## Context
Writing to the database and publishing to Kafka are two operations that cannot share a transaction. Doing them one
after the other loses events (crash between them) or publishes events for rolled-back changes.

## Decision
- Producers write an `outbox_event` row in the same transaction as the business change. A scheduled publisher relays
  rows to Kafka (`FOR UPDATE SKIP LOCKED`, safe with several instances).
- Delivery is therefore at-least-once. Consumers record each handled `eventId` in `processed_event` inside the same
  transaction as their own change and skip duplicates.
- The shared code lives in `platform-common` and is wired by Spring Boot auto-configuration, so services only call
  `outbox.publish(...)` and `idempotency.markProcessed(...)`.

## Alternatives considered
- Change data capture with Debezium: no polling latency and no publisher code, but one more moving part to run.
  The outbox table is already compatible with it, so it can replace the polling publisher later.
- Kafka transactions: only cover Kafka, not the database write.

## Consequences
- A small publishing latency (poll interval, default 500 ms).
- Ordering per aggregate is kept with a single publisher; with several competing publishers a CDC relay would be needed
  for strict ordering.
- Outbox rows need housekeeping (not implemented yet).
