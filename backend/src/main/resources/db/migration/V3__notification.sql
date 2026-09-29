create table notification_rule (
    id          bigserial primary key,
    target_type varchar(20)  not null unique,
    days_before varchar(100) not null,
    channels    varchar(100) not null,
    enabled     boolean      not null,
    created_at  timestamp    not null,
    created_by  varchar(50),
    updated_at  timestamp    not null,
    updated_by  varchar(50)
);

create table notification (
    id            bigserial primary key,
    target_type   varchar(20)  not null,
    target_id     bigint       not null,
    rule_days     int          not null,
    expiry_date   date         not null,
    recipient_id  bigint       not null references app_user (id),
    supplier_id   bigint       not null references supplier (id),
    title         varchar(200) not null,
    message       varchar(500) not null,
    action_status varchar(20)  not null,
    action_at     timestamp,
    created_at    timestamp    not null,
    constraint uk_notification_once unique (target_type, target_id, rule_days, expiry_date, recipient_id)
);
create index ix_notification_recipient on notification (recipient_id, action_status);

create table notification_delivery (
    id              bigserial primary key,
    notification_id bigint      not null references notification (id),
    channel         varchar(20) not null,
    status          varchar(20) not null,
    retry_count     int         not null,
    next_attempt_at timestamp   not null,
    last_error      varchar(500),
    sent_at         timestamp,
    created_at      timestamp   not null
);
create index ix_delivery_due on notification_delivery (status, next_attempt_at);

-- 알림 기준 기본값: 만료 90·30·7일 전, 화면 + 이메일 (미팅에서 확정)
insert into notification_rule (target_type, days_before, channels, enabled, created_at, updated_at) values
    ('CONTRACT', '90,30,7', 'IN_APP,EMAIL', true, now(), now()),
    ('CERTIFICATION', '90,30,7', 'IN_APP,EMAIL', true, now(), now());
