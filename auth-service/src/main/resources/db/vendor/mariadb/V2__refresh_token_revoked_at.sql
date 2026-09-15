-- V2 · P1-3 refresh 회전 동시성 — 폐기 시각 기록 (MariaDB 동판 — H2 용은 db/vendor/h2/V2).
-- "방금 회전됨(동시 요청 경쟁 패배 — 정상)"과 "오래전 폐기된 토큰 재제출(탈취 의심)"을
-- 구분하는 근거가 된다. 기존 행은 null(과거 폐기분) — 탈취 의심 쪽으로 보수 판정된다.
alter table refresh_tokens add column revoked_at datetime(6);
