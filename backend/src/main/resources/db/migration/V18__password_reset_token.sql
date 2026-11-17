-- 비밀번호 재설정 1회용 토큰 (#12, FR-029).
create table password_reset_token (
    id bigserial primary key,
    app_user_id bigint not null references app_user(id),
    token varchar(64) not null,
    expires_at timestamp not null,
    used_at timestamp,
    created_at timestamp not null,
    created_by varchar(50),
    updated_at timestamp not null,
    updated_by varchar(50)
);
create unique index ux_password_reset_token_token on password_reset_token(token);
create index ix_password_reset_token_user on password_reset_token(app_user_id, created_at desc);
