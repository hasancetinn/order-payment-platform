create table user_contact (
    user_id varchar(64)  primary key,
    email   varchar(255) not null
);

create table notification (
    id         uuid          primary key,
    user_id    varchar(64)   not null,
    recipient  varchar(255)  not null,
    subject    varchar(255)  not null,
    body       varchar(2000) not null,
    created_at timestamptz   not null
);

create index idx_notification_user_created on notification (user_id, created_at desc);
