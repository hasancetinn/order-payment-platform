-- Shared by every service that uses platform-common (applied before the service's own V1+ migrations).

create table outbox_event (
    id          bigserial primary key,
    event_id    uuid         not null unique,
    topic       varchar(100) not null,
    message_key varchar(100) not null,
    event_type  varchar(100) not null,
    payload     text         not null,
    status      varchar(20)  not null,
    created_at  timestamptz  not null,
    sent_at     timestamptz
);

create index idx_outbox_pending on outbox_event (id) where status = 'PENDING';

create table processed_event (
    event_id     uuid primary key,
    processed_at timestamptz not null
);
