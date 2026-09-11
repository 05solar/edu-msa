-- V3 · P0-2 배포 동시성 정합성 (PostgreSQL 전용 — H2 용은 db/migration/h2/V3)
--
-- 1) slug_claims: slug 소유권 예약. slug 가 PK 라서 동시 INSERT 경쟁에서 DB 가
--    단 한 배포만 승자로 만든다(애플리케이션 exists 검사는 TOCTOU 라 UX 용으로 강등).
-- 2) deploy_jobs 부분 유니크: 같은 프로그램의 active(QUEUED/RUNNING) 배포 작업은
--    동시에 1건만 존재(더블클릭·승인 중복·webhook replay·network retry 흡수).
--
-- 기존 데이터가 제약을 위반하면 무엇도 지우지 않고 명시적으로 실패한다 —
-- 운영자가 정당한 소유자를 판단해 정리한 뒤 재기동해야 한다.

-- ---------- 사전 검사 ① 서로 다른 프로그램이 같은 slug 를 점유한 이력 ----------
DO $$
DECLARE bad text;
BEGIN
  SELECT string_agg(t.slug, ', ') INTO bad FROM (
    SELECT slug FROM deployments WHERE slug IS NOT NULL
    GROUP BY slug HAVING COUNT(DISTINCT COALESCE(program_id, -1)) > 1
  ) t;
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'P0-2 마이그레이션 중단 — slug [%] 를 서로 다른 프로그램의 deployments 가 점유 중. 정당한 소유 프로그램을 판단해 잘못된 쪽을 프로그램 삭제 API(DELETE /api/programs/{id} — 배포 흔적 동반 정리)로 정리한 뒤 재기동하세요.', bad;
  END IF;
END $$;

-- ---------- slug 소유권 예약 테이블 ----------
create table slug_claims (
    slug varchar(64) primary key,
    program_id bigint,
    created_at timestamp(6) with time zone not null default current_timestamp
);

-- 기존 배포 이력에서 소유권을 승계한다(사전 검사 ① 통과 시 slug 당 정확히 1행).
insert into slug_claims (slug, program_id)
    select distinct slug, program_id from deployments where slug is not null;

-- ---------- 사전 검사 ② 같은 프로그램의 active 작업 중복 ----------
DO $$
DECLARE bad text;
BEGIN
  SELECT string_agg(t.program_id::text, ', ') INTO bad FROM (
    SELECT program_id FROM deploy_jobs
    WHERE status IN ('QUEUED','RUNNING') AND program_id IS NOT NULL
    GROUP BY program_id HAVING COUNT(*) > 1
  ) t;
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'P0-2 마이그레이션 중단 — 프로그램 [%] 에 active(QUEUED/RUNNING) 배포 작업이 2건 이상. 큐가 소진되기를 기다렸다가 재기동하거나, 중복 QUEUED 행을 운영자가 FAILED 로 정리한 뒤 재기동하세요.', bad;
  END IF;
END $$;

-- ---------- 프로그램당 active 배포 작업 1건 강제 ----------
create unique index uq_deploy_jobs_active_program on deploy_jobs (program_id)
    where status in ('QUEUED','RUNNING') and program_id is not null;
