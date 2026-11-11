create table user_account (
    id            uuid         primary key,
    email         varchar(255) not null unique,
    password_hash varchar(100) not null,
    role          varchar(30)  not null,
    created_at    timestamptz  not null
);
