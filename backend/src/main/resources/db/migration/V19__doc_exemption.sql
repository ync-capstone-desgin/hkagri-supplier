-- 필수 서류의 업체별 예외 (#85): 공통 필수 서류 중 그 업체에는 해당하지 않는 종류.
-- 행이 있으면 그 업체는 그 서류를 내지 않아도 누락으로 세지 않는다. 누가 언제 뺐는지 남긴다.
create table doc_exemption (
    id          bigserial primary key,
    supplier_id bigint      not null references supplier (id),
    category_id bigint      not null references file_category (id),
    created_at  timestamp   not null,
    created_by  varchar(50) not null,
    constraint uq_doc_exemption unique (supplier_id, category_id)
);
