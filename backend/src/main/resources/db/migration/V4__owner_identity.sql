-- V4 · P1-5 불변 identity 전환 — 소유권/알림 수신자를 표시 이름(변경 가능·중복 가능)이
-- 아니라 auth 계정의 불변 UID(JWT uid = accounts.id)로 판정한다.
--
-- 컬럼 의미:
--   programs.owner          → 표시 스냅샷(화면 전용 — 권한 판정 금지)
--   programs.owner_id       → 소유자 UID (권한 판정의 유일한 근거)
--   programs.owner_trusted  → 생성 시점 JWT role 이 내부(CODER/ADMIN)였는지 —
--                             배포 네임스페이스 신뢰 판정용(이름 문자열 신뢰 제거)
--   notifications.to_user   → 표시 스냅샷
--   notifications.recipient_id / recipient_role → 수신자 UID 또는 역할(관리자 공지)
--
-- 기존 데이터 backfill 은 하지 않는다: 표시 이름 → UID 매핑은 auth DB(분리)와의
-- 대조가 필요하고 동명이인 오매핑 위험이 있다(자동 추정 금지 원칙).
--   · owner_id NULL 인 기존 프로그램 = 소유자 불명 — 소유자 액션은 ADMIN 만 가능,
--     소유자가 필요하면 재등록하거나 관리자가 정리한다(명시적 remediation).
--   · recipient 가 모두 NULL 인 기존 알림 = 안전하게 귀속 불가 — 노출하지 않는다.
-- NOT NULL 전환은 legacy 행이 소멸된 뒤 후속 마이그레이션에서 수행한다(단계적 전환).

alter table programs add column owner_id bigint;
alter table programs add column owner_trusted boolean not null default false;
create index idx_programs_owner_id on programs (owner_id);

alter table notifications add column recipient_id bigint;
alter table notifications add column recipient_role varchar(255);
create index idx_notifications_recipient_read on notifications (recipient_id, is_read);
