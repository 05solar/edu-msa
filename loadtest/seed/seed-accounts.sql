-- 부하 테스트 계정 대량 시드 (auth-db / eduauth · MariaDB)
-- 전제: 절대 운영 DB 에 적용하지 않는다 — 부하 테스트 전용 환경에서만 실행.
--
-- bcrypt 해시는 로컬에서 만들 수 없으므로, 먼저 "템플릿 계정" 1개를 회원가입 API 로 만들고
-- 그 password_hash 를 복제한다(모든 lt_user_* 가 같은 비밀번호 LoadTest#2026! 를 공유):
--
--   curl -X POST $AUTH/api/auth/signup -H 'Content-Type: application/json' -d '{
--     "username":"lt_template","password":"LoadTest#2026!","name":"부하템플릿",
--     "email":"lt_template@loadtest.local","dept":"부하테스트"}'
--
-- 실행(계정 수는 @count — 미지정 시 200000):
--   ( echo "SET @count=200000;"; cat loadtest/seed/seed-accounts.sql ) \
--     | mariadb -h <host> -ueduauth -p eduauth

SET @count = IFNULL(@count, 200000);
SET SESSION max_recursive_iterations = 1000000;

INSERT IGNORE INTO accounts (username, password_hash, name, email, dept, role,
                             must_change_password, created_at, updated_at)
SELECT
  CONCAT('lt_user_', LPAD(i, 6, '0')),
  (SELECT password_hash FROM accounts WHERE username = 'lt_template'),
  CONCAT('부하사용자', i),
  CONCAT('lt_user_', LPAD(i, 6, '0'), '@loadtest.local'),
  '부하테스트',
  'USER',
  false, NOW(6), NOW(6)
FROM (WITH RECURSIVE seq(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM seq WHERE i < @count)
      SELECT i FROM seq) s;

SELECT COUNT(*) AS loadtest_accounts FROM accounts WHERE username LIKE 'lt\_user\_%';
