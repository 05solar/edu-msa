-- V5 · P2-1 알림 페이지네이션 + 장기 보존 정책.
--
-- created_at: 최신순 페이지 정렬(created_at DESC, id DESC — id 가 tie-breaker)과
-- 보존 정책(읽은 알림만 retention 경과 후 삭제)의 기준. 기존 행은 마이그레이션
-- 시각으로 backfill 한다 — 보수적(legacy 행도 지금부터 retention 을 다시 센다).
--
-- 인덱스: 목록 쿼리는 `recipient_id = ? OR recipient_role = ?` 라 단일 인덱스로
-- 못 태우고 PostgreSQL 이 두 인덱스를 BitmapOr 로 합친다 — 각각의 축을 둔다.
alter table notifications add column created_at timestamp(6) with time zone;
update notifications set created_at = current_timestamp;
alter table notifications alter column created_at set not null;

create index idx_notifications_recipient_created on notifications (recipient_id, created_at);
create index idx_notifications_role_created on notifications (recipient_role, created_at);
