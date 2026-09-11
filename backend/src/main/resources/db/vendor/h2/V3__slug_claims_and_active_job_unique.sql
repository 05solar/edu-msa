-- V3 · P0-2 (H2 테스트 전용) — PostgreSQL 원본(db/migration/postgresql/V3)과 동판.
-- H2 는 partial unique index 와 DO 블록을 지원하지 않아 slug_claims 만 만든다.
-- deploy_jobs 의 active 중복 방지(부분 유니크)는 PostgreSQL 에서만 강제되며,
-- 그 경로는 실제 PostgreSQL/staging 에서 검증한다.

create table slug_claims (
    slug varchar(64) primary key,
    program_id bigint,
    created_at timestamp(6) with time zone not null default current_timestamp
);

insert into slug_claims (slug, program_id)
    select distinct slug, program_id from deployments where slug is not null;
