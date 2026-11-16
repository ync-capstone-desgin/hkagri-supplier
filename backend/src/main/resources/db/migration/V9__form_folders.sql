-- 기존 양식은 최상위 폴더에 보존하고 하위 폴더만 추가한다.
create table form_folder (
    id bigserial primary key,
    parent_id bigint references form_folder(id),
    name varchar(100) not null,
    deleted_at timestamp,
    deleted_by varchar(50),
    created_at timestamp not null,
    created_by varchar(50),
    updated_at timestamp not null,
    updated_by varchar(50),
    check ((id = 1 and parent_id is null) or (id <> 1 and parent_id is not null))
);
insert into form_folder (name, created_at, updated_at) values ('양식 자료실', current_timestamp, current_timestamp);
create unique index ux_form_folder_name on form_folder(parent_id, lower(name)) where deleted_at is null;
alter table form_post add column folder_id bigint not null default 1 references form_folder(id);
create index ix_form_post_folder on form_post(folder_id, created_at desc, id desc) where deleted_at is null;
