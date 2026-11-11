# ADR 0001: Choreography-based saga for the order flow

**Status:** accepted

## Context
Creating an order touches three services with three databases (order, inventory, payment). A distributed
transaction (2PC/XA) would couple availability of all services and does not exist across Kafka and PostgreSQL anyway.

## Decision
Use a saga: each service commits a local transaction and publishes an event; the next service reacts. Failures are
handled by compensating events (`PaymentFailed` makes inventory release the stock and the order cancel itself).
We use choreography (no central coordinator) because the flow is short and linear.

## Consequences
- No service is blocked waiting for another; the order is `PENDING` until the saga finishes, the API answers `202`.
- The flow is implicit in event names. For longer flows with many branches an orchestrator (e.g. a process
  manager inside order-service, or Temporal) would be easier to reason about.
- Every step must be idempotent and tolerate late/duplicate events (see ADR 0002).
