create table app_user (
    id          bigserial primary key,
    username    varchar(50)  not null unique,
    password    varchar(100) not null,
    name        varchar(50)  not null,
    email       varchar(100),
    role        varchar(20)  not null,
    created_at  timestamp    not null,
    created_by  varchar(50),
    updated_at  timestamp    not null,
    updated_by  varchar(50)
);

create table supplier (
    id              bigserial primary key,
    code            varchar(20) unique,
    name            varchar(100) not null,
    business_number varchar(20),
    status          varchar(20)  not null,
    contact_name    varchar(50),
    contact_phone   varchar(30),
    contact_email   varchar(100),
    owner_id        bigint references app_user (id),
    memo            varchar(1000),
    created_at      timestamp    not null,
    created_by      varchar(50),
    updated_at      timestamp    not null,
    updated_by      varchar(50)
);
