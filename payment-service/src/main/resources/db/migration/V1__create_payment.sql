create table payment (
    id                 uuid primary key,
    order_id           uuid           not null unique,
    user_id            varchar(64)    not null,
    amount             numeric(19, 2) not null,
    currency           varchar(3)     not null,
    status             varchar(20)    not null,
    provider_reference varchar(100),
    failure_reason     varchar(255),
    created_at         timestamptz    not null
);

create index idx_payment_user on payment (user_id);
