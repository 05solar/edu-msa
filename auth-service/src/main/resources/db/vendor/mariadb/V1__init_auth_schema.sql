-- V1 · 인증(auth-service) 초기 스키마 (MariaDB 동판 — H2 테스트용은 db/vendor/h2/V1)
-- Hibernate 가 엔티티에서 기대하는 DDL 을 기준으로 작성했다. 애플리케이션은 validate 로만 검증한다.
-- 기존 DB(이미 스키마 존재)는 flyway baseline-on-migrate(baseline-version=1)로 V1 을 건너뛴다.
--
-- MariaDB 방언: identity → auto_increment, timestamp with time zone → datetime(6).
-- 시각은 앱(OffsetDateTime/UTC)과 커넥터(timezone=UTC)가 UTC 로 고정해 저장한다.

create table accounts (
    id bigint auto_increment,
    username varchar(20) not null unique,
    password_hash varchar(100) not null,
    name varchar(50) not null,
    email varchar(190) not null unique,
    dept varchar(50) not null,
    role varchar(10) not null check (role in ('USER','CODER','ADMIN')),
    requested_role varchar(10) check (requested_role in ('USER','CODER','ADMIN')),
    role_request_reason varchar(300),
    must_change_password boolean not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
);

create table refresh_tokens (
    id bigint auto_increment,
    account_id bigint not null,
    token_hash varchar(64) not null unique,
    expires_at datetime(6) not null,
    revoked boolean not null,
    created_at datetime(6) not null,
    primary key (id)
);

create index ix_refresh_account on refresh_tokens (account_id);
create index ix_refresh_expires on refresh_tokens (expires_at);
