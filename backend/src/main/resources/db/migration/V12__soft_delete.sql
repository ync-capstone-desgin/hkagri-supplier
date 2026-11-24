-- 협력업체·계약·인증 삭제 (#44): 행을 지우지 않고 지운 표시만 남긴다.
-- deleted_at이 있으면 모든 조회에서 빠지고, 누가 언제 지웠는지 남는다.
alter table supplier add column deleted_at timestamp, add column deleted_by varchar(50);
alter table contract add column deleted_at timestamp, add column deleted_by varchar(50);
alter table certification add column deleted_at timestamp, add column deleted_by varchar(50);
