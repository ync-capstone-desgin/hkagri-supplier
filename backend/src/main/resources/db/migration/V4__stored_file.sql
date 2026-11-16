-- 첨부파일 정보 (FR-021, FR-022 공통)
-- 파일 내용은 저장소(서버 디스크 등)에 두고, 여기에는 이름·크기·형식과 저장 위치(키)만 기록한다.
-- 올린 사람·시각은 created_by·created_at을 쓴다.
-- 삭제는 행을 지우지 않고 deleted_at·deleted_by를 채워 이력을 남긴다 (NFR-007).
create table stored_file (
    id            bigserial primary key,
    original_name varchar(255) not null,
    content_type  varchar(150) not null,
    size_bytes    bigint       not null,
    storage_key   varchar(300) not null unique,
    deleted_at    timestamp,
    deleted_by    varchar(50),
    created_at    timestamp    not null,
    created_by    varchar(50),
    updated_at    timestamp    not null,
    updated_by    varchar(50)
);
create index ix_stored_file_deleted_at on stored_file (deleted_at);
