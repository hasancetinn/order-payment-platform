# ADR 0003: Database per service, Flyway migrations

**Status:** accepted

## Decision
Each service has its own PostgreSQL database (separate databases on one server for local development) and is the only
one that reads or writes it. Data needed elsewhere travels in events (for example the user's e-mail is copied into
`notification-service` from `UserRegistered`). Schemas are versioned with Flyway, Hibernate only validates
(`ddl-auto: validate`). The outbox and processed-event tables are shipped by `platform-common` as migration
`V0_1`, applied before each service's own `V1+` scripts.

## Consequences
- Services can be deployed and scaled independently, no shared-schema coupling.
- No joins across services; read models are built from events when needed.
- Shared tables must be kept backward compatible when `platform-common` changes.
