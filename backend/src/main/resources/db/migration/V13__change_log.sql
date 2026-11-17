-- 변경 이력 (#45): 협력업체·계약·인증을 누가 언제 등록·수정·갱신·삭제했는지 남긴다.
-- 대상이 삭제되거나 이름이 바뀌어도 읽을 수 있게 이름은 기록 시점의 값으로 저장하고, 외래키는 걸지 않는다.
create table change_log (
    id            bigserial primary key,
    target_type   varchar(20)  not null,
    target_id     bigint       not null,
    supplier_id   bigint       not null,
    supplier_name varchar(100) not null,
    target_name   text         not null,
    action        varchar(20)  not null,
    changes       text         not null,
    actor         varchar(50)  not null,
    actor_name    varchar(50)  not null,
    created_at    timestamp    not null
);
create index ix_change_log_supplier on change_log (supplier_id, created_at desc);
create index ix_change_log_created on change_log (created_at desc);
