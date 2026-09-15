-- V2 · 배포 큐 재시도 백오프 + 상태 인덱스 (MariaDB 동판 — H2 용은 db/vendor/h2/V2)
-- next_attempt_at: 재시도 지수 백오프 — 이 시각 전에는 claim 대상에서 제외.
-- idx_deploy_jobs_status: 큐 폴링(claim)·상태별 카운트 메트릭용.
-- IF NOT EXISTS: ddl-auto=update 로 최신 엔티티 스키마가 이미 만들어진 DB(개발 등)에서
-- baseline 후 V2 가 실행돼도 충돌하지 않게 멱등으로 작성한다.
alter table deploy_jobs add column if not exists next_attempt_at datetime(6);
create index if not exists idx_deploy_jobs_status on deploy_jobs (status);
