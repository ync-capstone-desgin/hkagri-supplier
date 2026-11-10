-- V6(#29), V7(#30) 이후 적용하는 사내 공용 양식 게시글과 첨부 연결.
create table form_post (
    id bigserial primary key,
    title varchar(200) not null,
    description varchar(5000),
    category varchar(20) not null check (category in ('CONTRACT', 'CHECKLIST', 'OTHER')),
    version bigint not null default 0,
    deleted_at timestamp,
    deleted_by varchar(50),
    created_at timestamp not null,
    created_by varchar(50),
    updated_at timestamp not null,
    updated_by varchar(50)
);
create table form_post_file (
    post_id bigint not null references form_post(id),
    file_id bigint not null unique references stored_file(id),
    primary key (post_id, file_id)
);
create index ix_form_post_recent on form_post(created_at desc, id desc) where deleted_at is null;
create index ix_form_post_category on form_post(category, created_at desc, id desc) where deleted_at is null;
