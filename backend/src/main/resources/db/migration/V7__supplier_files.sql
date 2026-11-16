-- V6는 마이페이지 PR #29에서 사용한다.
-- 파일은 업체·계약·인증 중 한 곳에만 연결한다. 기존 공통 업로드 파일은 연결 없이 유지한다.
alter table stored_file
    add column supplier_id bigint references supplier(id),
    add column contract_id bigint references contract(id),
    add column certification_id bigint references certification(id),
    add constraint ck_stored_file_target check (num_nonnulls(supplier_id, contract_id, certification_id) <= 1);

create index ix_stored_file_supplier on stored_file(supplier_id, created_at desc, id desc) where deleted_at is null;
create index ix_stored_file_contract on stored_file(contract_id) where deleted_at is null;
create index ix_stored_file_certification on stored_file(certification_id) where deleted_at is null;
