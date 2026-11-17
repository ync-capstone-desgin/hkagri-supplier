-- 필수 서류 점검 (#47): 자료 종류에 필수 여부를 둔다.
-- 처음에는 아무 종류도 필수가 아니다. 관리자가 자료 종류 관리에서 정한다.
alter table file_category add column required boolean not null default false;
