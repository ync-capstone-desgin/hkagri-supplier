-- 마이페이지(FR-028): 사용자별 알림메일 수신 여부. 기본은 받음(true)이다.
alter table app_user add column notify_email boolean not null default true;
