create table product (
    sku                varchar(64)    primary key,
    name               varchar(255)   not null,
    price              numeric(19, 2) not null,
    available_quantity int            not null check (available_quantity >= 0),
    version            bigint         not null default 0
);

create table stock_reservation (
    id         bigserial   primary key,
    order_id   uuid        not null,
    sku        varchar(64) not null references product (sku),
    quantity   int         not null check (quantity > 0),
    status     varchar(20) not null,
    created_at timestamptz not null
);

create index idx_stock_reservation_order on stock_reservation (order_id);
