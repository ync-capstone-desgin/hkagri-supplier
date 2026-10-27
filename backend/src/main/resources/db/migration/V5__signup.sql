-- 기존 사용자·시연 계정은 계속 사용할 수 있게 ACTIVE로 이관한다.
alter table app_user add column status varchar(20) not null default 'ACTIVE';
alter table app_user add column signup_reason varchar(1000);
alter table app_user add constraint ck_app_user_status
    check (status in ('PENDING', 'ACTIVE', 'REJECTED', 'INACTIVE'));

-- 동시 가입 요청에서도 같은 이메일이 중복 저장되지 않게 한다.
create unique index uq_app_user_email_lower on app_user (lower(email));
