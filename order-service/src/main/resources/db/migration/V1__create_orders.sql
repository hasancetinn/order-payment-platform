create table orders (
    id                  uuid primary key,
    user_id             varchar(64)   not null,
    status              varchar(20)   not null,
    total_amount        numeric(19, 2) not null,
    currency            varchar(3)    not null,
    cancellation_reason varchar(255),
    idempotency_key     varchar(100),
    created_at          timestamptz   not null,
    updated_at          timestamptz   not null,
    version             bigint        not null
);

-- same user + same Idempotency-Key can only ever create one order (safety net for concurrent retries)
create unique index uq_orders_user_idempotency_key
    on orders (user_id, idempotency_key) where idempotency_key is not null;

create index idx_orders_user_created on orders (user_id, created_at desc);

create table order_item (
    id         bigserial primary key,
    order_id   uuid          not null references orders (id),
    sku        varchar(64)   not null,
    quantity   int           not null,
    unit_price numeric(19, 2) not null
);

create index idx_order_item_order on order_item (order_id);
