-- 필수 인증 만료 표시 (#46): 인증 종류에 필수 여부를 둔다.
-- 처음에는 아무 종류도 필수가 아니다. 관리자가 인증 종류 관리에서 정한다.
alter table cert_type add column required boolean not null default false;
