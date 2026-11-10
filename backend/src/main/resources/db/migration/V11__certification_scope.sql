alter table certification add column scope varchar(200);

-- 업체명·인증 종류·적용 대상이 최대 길이여도 알림을 저장할 수 있어야 한다.
alter table notification alter column title type varchar(500);
alter table notification alter column message type varchar(1000);
