-- 부하 테스트 계정 대량 시드 (auth-db / eduauth)
-- 전제: 절대 운영 DB 에 적용하지 않는다 — 부하 테스트 전용 환경에서만 실행.
--
-- bcrypt 해시는 로컬에서 만들 수 없으므로, 먼저 "템플릿 계정" 1개를 회원가입 API 로 만들고
-- 그 password_hash 를 복제한다(모든 lt_user_* 가 같은 비밀번호 LoadTest#2026! 를 공유):
--
--   curl -X POST $AUTH/api/auth/signup -H 'Content-Type: application/json' -d '{
--     "username":"lt_template","password":"LoadTest#2026!","name":"부하템플릿",
--     "email":"lt_template@loadtest.local","dept":"부하테스트"}'
--
-- 실행(계정 수 조정은 :count):
--   psql "$AUTH_DB_URL" -v count=200000 -f loadtest/seed/seed-accounts.sql
\set count :count

INSERT INTO accounts (username, password_hash, name, email, dept, role,
                      must_change_password, created_at, updated_at)
SELECT
  'lt_user_' || lpad(i::text, 6, '0'),
  (SELECT password_hash FROM accounts WHERE username = 'lt_template'),
  '부하사용자' || i,
  'lt_user_' || lpad(i::text, 6, '0') || '@loadtest.local',
  '부하테스트',
  'USER',
  false, now(), now()
FROM generate_series(1, :count) AS i
ON CONFLICT (username) DO NOTHING;

SELECT count(*) AS loadtest_accounts FROM accounts WHERE username LIKE 'lt\_user\_%';
