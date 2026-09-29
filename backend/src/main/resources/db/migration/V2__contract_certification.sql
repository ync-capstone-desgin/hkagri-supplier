create table cert_type (
    code       varchar(30) primary key,
    name       varchar(100) not null,
    sort_order int          not null
);

create table contract (
    id              bigserial primary key,
    supplier_id     bigint       not null references supplier (id),
    contract_number varchar(50)  not null,
    title           varchar(200) not null,
    start_date      date         not null,
    end_date        date,
    auto_renew      boolean      not null,
    renewal_status  varchar(20)  not null,
    owner_id        bigint references app_user (id),
    prev_id         bigint references contract (id),
    active          boolean      not null,
    document_url    varchar(500),
    memo            varchar(1000),
    created_at      timestamp    not null,
    created_by      varchar(50),
    updated_at      timestamp    not null,
    updated_by      varchar(50)
);
create index ix_contract_supplier on contract (supplier_id);
create index ix_contract_end_date on contract (end_date);

create table certification (
    id                 bigserial primary key,
    supplier_id        bigint      not null references supplier (id),
    cert_type_code     varchar(30) not null references cert_type (code),
    certificate_number varchar(50) not null,
    issued_date        date        not null,
    expiry_date        date,
    renewal_status     varchar(20) not null,
    owner_id           bigint references app_user (id),
    prev_id            bigint references certification (id),
    active             boolean     not null,
    document_url       varchar(500),
    memo               varchar(1000),
    created_at         timestamp   not null,
    created_by         varchar(50),
    updated_at         timestamp   not null,
    updated_by         varchar(50)
);
create index ix_certification_supplier on certification (supplier_id);
create index ix_certification_expiry_date on certification (expiry_date);

-- 인증 종류는 예시값이다. 실제 관리 대상 인증은 기업 미팅에서 확인한다.
insert into cert_type (code, name, sort_order) values
    ('HACCP', 'HACCP(식품안전관리인증)', 1),
    ('GAP', 'GAP(농산물우수관리인증)', 2),
    ('ORGANIC', '유기농산물 인증', 3),
    ('ECO', '무농약농산물 인증', 4),
    ('ISO9001', 'ISO 9001', 5),
    ('ISO22000', 'ISO 22000', 6);
