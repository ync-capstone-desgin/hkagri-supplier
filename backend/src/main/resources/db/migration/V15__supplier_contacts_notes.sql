-- 협력업체 추가 담당자와 소통 메모 (#49).
-- 기본정보의 업체 담당자(대표 담당자)는 supplier 테이블에 그대로 두고, 그 밖의 담당자를 여러 명 둔다.
create table supplier_contact (
    id          bigserial primary key,
    supplier_id bigint       not null references supplier (id),
    name        varchar(50)  not null,
    title       varchar(50),
    phone       varchar(30),
    email       varchar(100),
    created_at  timestamp    not null,
    created_by  varchar(50),
    updated_at  timestamp    not null,
    updated_by  varchar(50),
    deleted_at  timestamp,
    deleted_by  varchar(50)
);
create index ix_supplier_contact_supplier on supplier_contact (supplier_id);

-- 소통 메모: 수정은 없고, 삭제는 지운 표시만 남긴다. contact_date는 실제로 소통한 날짜다.
create table supplier_note (
    id           bigserial primary key,
    supplier_id  bigint      not null references supplier (id),
    contact_date date        not null,
    content      text        not null,
    author_name  varchar(50) not null,
    created_at   timestamp   not null,
    created_by   varchar(50),
    updated_at   timestamp   not null,
    updated_by   varchar(50),
    deleted_at   timestamp,
    deleted_by   varchar(50)
);
create index ix_supplier_note_supplier on supplier_note (supplier_id, contact_date desc);
