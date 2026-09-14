-- V3 · P0-2 (H2 테스트 전용) — MariaDB 원본(db/vendor/mariadb/V3)과 동판.
-- H2 는 IF() 기반 STORED 생성 컬럼과 SIGNAL 사전 검사를 지원하지 않아 slug_claims 만 만든다.
-- deploy_jobs 의 active 중복 방지(생성 컬럼 유니크)는 MariaDB 에서만 강제되며,
-- 그 경로는 MariaDbDeployConcurrencyIT(Testcontainers)가 실 DB 로 검증한다.

create table slug_claims (
    slug varchar(64) primary key,
    program_id bigint,
    created_at timestamp(6) with time zone not null default current_timestamp
);

insert into slug_claims (slug, program_id)
    select distinct slug, program_id from deployments where slug is not null;
