-- 자료 종류 (#54): 업체 자료가 어떤 서류인지 구분한다. 관리자가 추가·이름 수정·사용 중지를 한다.
create table file_category (
    id         bigserial primary key,
    name       varchar(100) not null,
    -- 일부 유니코드 문자는 소문자 변환 후 길이가 늘어나므로 표시 이름보다 여유를 둔다.
    name_key   varchar(300) not null,
    active     boolean      not null default true,
    sort_order int          not null,
    constraint uq_file_category_name_key unique (name_key)
);

-- 처음 넣어 두는 종류. 일반적인 서류 이름이며, 한국농산 답변을 받으면 관리자 화면에서 고친다.
insert into file_category (name, name_key, sort_order) values
    ('사업자등록증', '사업자등록증', 1),
    ('통장 사본', '통장사본', 2),
    ('계약서', '계약서', 3),
    ('인증서', '인증서', 4),
    ('표기사항', '표기사항', 5),
    ('품목제조보고서', '품목제조보고서', 6),
    ('성적서', '성적서', 7);

-- 파일마다 종류 하나를 붙인다 (선택).
alter table stored_file add column category_id bigint references file_category (id);
create index ix_stored_file_category on stored_file (category_id);
