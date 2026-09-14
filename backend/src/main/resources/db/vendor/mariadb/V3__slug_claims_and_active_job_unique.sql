-- V3 · P0-2 배포 동시성 정합성 (MariaDB 동판 — H2 테스트용은 db/vendor/h2/V3)
--
-- 1) slug_claims: slug 소유권 예약. slug 가 PK 라서 동시 INSERT 경쟁에서 DB 가
--    단 한 배포만 승자로 만든다(애플리케이션 exists 검사는 TOCTOU 라 UX 용으로 강등).
-- 2) deploy_jobs 활성 작업 유니크: 같은 프로그램의 active(QUEUED/RUNNING) 배포 작업은
--    동시에 1건만 존재(더블클릭·승인 중복·webhook replay·network retry 흡수).
--    MariaDB 는 부분 유니크 인덱스(WHERE 절)가 없어 STORED 생성 컬럼으로 대체한다:
--      active_program_id = active 상태일 때만 program_id, 아니면 NULL
--    유니크 인덱스는 NULL 중복을 허용하므로 DONE/FAILED 작업은 제약에서 자동으로 빠진다.
--    PostgreSQL 부분 인덱스(uq_deploy_jobs_active_program ... WHERE status IN (...))와
--    의미상 동일한 제약이며, 실측 검증은 MariaDbDeployConcurrencyIT 가 수행한다.
--
-- 기존 데이터가 제약을 위반하면 무엇도 지우지 않고 명시적으로 실패한다(사전 검사 SIGNAL) —
-- 운영자가 정당한 소유자를 판단해 정리한 뒤 재기동해야 한다.
-- 주의: MariaDB DDL 은 트랜잭션 롤백이 안 되므로 사전 검사를 모든 DDL 앞에 둔다.

-- ---------- 사전 검사 ① 서로 다른 프로그램이 같은 slug 를 점유한 이력 ----------
begin not atomic
  declare bad text;
  select group_concat(t.slug separator ', ') into bad from (
    select slug from deployments where slug is not null
    group by slug having count(distinct coalesce(program_id, -1)) > 1
  ) t;
  if bad is not null then
    signal sqlstate '45000' set message_text =
      'P0-2 마이그레이션 중단 — 같은 slug 를 서로 다른 프로그램의 deployments 가 점유 중. 정당한 소유 프로그램을 판단해 잘못된 쪽을 프로그램 삭제 API(DELETE /api/programs/{id})로 정리한 뒤 재기동하세요.';
  end if;
end;

-- ---------- 사전 검사 ② 같은 프로그램의 active 작업 중복 ----------
begin not atomic
  declare bad text;
  select group_concat(t.program_id separator ', ') into bad from (
    select program_id from deploy_jobs
    where status in ('QUEUED','RUNNING') and program_id is not null
    group by program_id having count(*) > 1
  ) t;
  if bad is not null then
    signal sqlstate '45000' set message_text =
      'P0-2 마이그레이션 중단 — active(QUEUED/RUNNING) 배포 작업이 2건 이상인 프로그램 존재. 큐 소진을 기다리거나 중복 QUEUED 행을 운영자가 FAILED 로 정리한 뒤 재기동하세요.';
  end if;
end;

-- ---------- slug 소유권 예약 테이블 ----------
create table slug_claims (
    slug varchar(64) primary key,
    program_id bigint,
    created_at datetime(6) not null default current_timestamp(6)
);

-- 기존 배포 이력에서 소유권을 승계한다(사전 검사 ① 통과 시 slug 당 정확히 1행).
insert into slug_claims (slug, program_id)
    select distinct slug, program_id from deployments where slug is not null;

-- ---------- 프로그램당 active 배포 작업 1건 강제 (생성 컬럼 + 유니크) ----------
-- STORED 생성 컬럼: 애플리케이션은 이 컬럼을 매핑하지 않는다(DB 전용 장치 — INSERT/UPDATE 시
-- status/program_id 로부터 자동 계산). 제약 위반은 Spring 예외 변환을 거쳐 기존
-- DataIntegrityViolationException 처리 경로(GlobalExceptionHandler 409)로 흘러간다.
alter table deploy_jobs add column active_program_id bigint
    as (if(status in ('QUEUED','RUNNING'), program_id, null)) stored;
create unique index uq_deploy_jobs_active_program on deploy_jobs (active_program_id);
