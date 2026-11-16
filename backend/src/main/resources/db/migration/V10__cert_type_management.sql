-- 기존 코드와 인증 FK를 유지하고 모든 기존 종류를 사용 상태로 보존한다.
alter table cert_type add column active boolean not null default true;
-- 일부 유니코드 문자는 소문자 변환 후 길이가 늘어나므로 표시 이름보다 여유를 둔다.
alter table cert_type add column name_key varchar(300);
update cert_type set name_key = lower(regexp_replace(name, '[[:space:]]', '', 'g'));
alter table cert_type alter column name_key set not null;
alter table cert_type add constraint uq_cert_type_name_key unique (name_key);
