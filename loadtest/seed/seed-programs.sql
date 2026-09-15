-- 부하 테스트 프로그램 대량 시드 (platform-db / edumsa · MariaDB)
-- 전제: 부하 테스트 전용 환경에서만 실행. 카탈로그가 시드 7건뿐이면 페이지네이션·검색·
-- 인덱스 효과를 실측할 수 없으므로 현실 규모(수백~수천 건)로 채운다.
--
-- 실행(건수는 @count — 미지정 시 2000):
--   ( echo "SET @count=2000;"; cat loadtest/seed/seed-programs.sql ) \
--     | mariadb -h <host> -uedumsa -p edumsa

SET @count = IFNULL(@count, 2000);
SET SESSION max_recursive_iterations = 1000000;

INSERT IGNORE INTO programs (name, slug, cat, owner, dept, app_version, summary, description,
                             repo_url, branch, status, scope, views, likes, downloads,
                             created_at, updated_at)
SELECT
  CONCAT('부하 프로그램 ', i),
  CONCAT('lt-prog-', LPAD(i, 6, '0')),
  ELT(1 + (i % 7), 'doc','student','curri','budget','facil','data','civil'),
  CONCAT('부하사용자', 1 + (i % 100)),
  '부하테스트',
  CONCAT('1.0.', i % 10),
  CONCAT('부하 테스트용 요약 — 검사 엑셀 생성 통계 계산 ', i),
  CONCAT('부하 테스트용 설명 본문 ocr 추출 변환 ', i),
  'sample://test-code',
  'main',
  'PUBLIC',
  IF(i % 10 = 0, 'DEPT', 'ALL'),
  (i * 37) % 5000,
  (i * 13) % 300,
  (i * 7) % 1000,
  CURRENT_DATE - INTERVAL (i % 365) DAY,
  CURRENT_DATE - INTERVAL (i % 90) DAY
FROM (WITH RECURSIVE seq(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM seq WHERE i < @count)
      SELECT i FROM seq) s;

-- 컬렉션 테이블(태그/기술/기능유형/실행방식)도 채워 배치 페치·EXISTS 검색 경로를 실측한다
INSERT INTO program_tags (program_id, tag)
SELECT p.id, t.v
FROM programs p
JOIN (SELECT '검사' AS v UNION ALL SELECT '엑셀' UNION ALL SELECT '자동화') t
WHERE p.slug LIKE 'lt-prog-%'
  AND NOT EXISTS (SELECT 1 FROM program_tags x WHERE x.program_id = p.id);

INSERT INTO program_tech (program_id, tech)
SELECT p.id, t.v
FROM programs p
JOIN (SELECT 'Python' AS v UNION ALL SELECT 'Go') t
WHERE p.slug LIKE 'lt-prog-%'
  AND NOT EXISTS (SELECT 1 FROM program_tech x WHERE x.program_id = p.id);

INSERT INTO program_purposes (program_id, purpose)
SELECT p.id, t.v
FROM programs p
JOIN (SELECT 'check' AS v UNION ALL SELECT 'convert') t
WHERE p.slug LIKE 'lt-prog-%'
  AND NOT EXISTS (SELECT 1 FROM program_purposes x WHERE x.program_id = p.id);

INSERT INTO program_run (program_id, run_type)
SELECT p.id, 'gitea'
FROM programs p
WHERE p.slug LIKE 'lt-prog-%'
  AND NOT EXISTS (SELECT 1 FROM program_run x WHERE x.program_id = p.id);

SELECT COUNT(*) AS loadtest_programs FROM programs WHERE slug LIKE 'lt-prog-%';
