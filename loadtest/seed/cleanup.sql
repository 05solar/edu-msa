-- 부하 테스트 데이터 정리 — lt_ / lt-prog- 접두어만 제거하므로 다른 데이터에 영향 없다.
-- 실행 (--force: 상대 DB 테이블이 없다는 오류를 건너뛰고 계속 진행):
--   mariadb --force -h <host> -uedumsa  -p edumsa  < loadtest/seed/cleanup.sql   # platform-db 부분
--   mariadb --force -h <host> -ueduauth -p eduauth < loadtest/seed/cleanup.sql   # auth-db 부분

-- platform-db (edumsa)
DELETE FROM program_tags     WHERE program_id IN (SELECT id FROM programs WHERE slug LIKE 'lt-prog-%');
DELETE FROM program_tech     WHERE program_id IN (SELECT id FROM programs WHERE slug LIKE 'lt-prog-%');
DELETE FROM program_purposes WHERE program_id IN (SELECT id FROM programs WHERE slug LIKE 'lt-prog-%');
DELETE FROM program_run      WHERE program_id IN (SELECT id FROM programs WHERE slug LIKE 'lt-prog-%');
DELETE FROM program_features WHERE program_id IN (SELECT id FROM programs WHERE slug LIKE 'lt-prog-%');
DELETE FROM program_readme   WHERE program_id IN (SELECT id FROM programs WHERE slug LIKE 'lt-prog-%');
DELETE FROM program_history  WHERE program_id IN (SELECT id FROM programs WHERE slug LIKE 'lt-prog-%');
DELETE FROM program_files    WHERE program_id IN (SELECT id FROM programs WHERE slug LIKE 'lt-prog-%');
DELETE FROM comments         WHERE program_id IN (SELECT id FROM programs WHERE slug LIKE 'lt-prog-%');
DELETE FROM deployments      WHERE program_id IN (SELECT id FROM programs WHERE slug LIKE 'lt-prog-%');
DELETE FROM deploy_jobs      WHERE actor = 'loadtest'
                                OR program_id IN (SELECT id FROM programs WHERE slug LIKE 'lt-prog-%');
DELETE FROM programs         WHERE slug LIKE 'lt-prog-%';
DELETE FROM notifications    WHERE to_user LIKE '부하사용자%';

-- auth-db (eduauth) — platform-db 에서 실행하면 두 문장은 테이블이 없어 오류가 나도 무방
DELETE FROM refresh_tokens WHERE account_id IN
  (SELECT id FROM accounts WHERE username LIKE 'lt\_user\_%' OR username = 'lt_template');
DELETE FROM accounts WHERE username LIKE 'lt\_user\_%' OR username = 'lt_template';
