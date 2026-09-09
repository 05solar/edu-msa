-- 부하 테스트 프로그램 대량 시드 (platform-db / edumsa)
-- 전제: 부하 테스트 전용 환경에서만 실행. 카탈로그가 시드 7건뿐이면 페이지네이션·검색·
-- 인덱스 효과를 실측할 수 없으므로 현실 규모(수백~수천 건)로 채운다.
--
-- 실행:  psql "$DB_URL" -v count=2000 -f loadtest/seed/seed-programs.sql
\set count :count

WITH ins AS (
  INSERT INTO programs (name, slug, cat, owner, dept, app_version, summary, description,
                        repo_url, branch, status, scope, views, likes, downloads,
                        created_at, updated_at)
  SELECT
    '부하 프로그램 ' || i,
    'lt-prog-' || lpad(i::text, 6, '0'),
    (ARRAY['doc','student','curri','budget','facil','data','civil'])[1 + (i % 7)],
    '부하사용자' || (1 + (i % 100)),
    '부하테스트',
    '1.0.' || (i % 10),
    '부하 테스트용 요약 — 검사 엑셀 생성 통계 계산 ' || i,
    '부하 테스트용 설명 본문 ocr 추출 변환 ' || i,
    'sample://test-code',
    'main',
    'PUBLIC',
    CASE WHEN i % 10 = 0 THEN 'DEPT' ELSE 'ALL' END,
    (i * 37) % 5000,
    (i * 13) % 300,
    (i * 7) % 1000,
    current_date - ((i % 365))::int,
    current_date - ((i % 90))::int
  FROM generate_series(1, :count) AS i
  ON CONFLICT (slug) DO NOTHING
  RETURNING id
)
-- 컬렉션 테이블(태그/기술/기능유형/실행방식)도 채워 배치 페치·EXISTS 검색 경로를 실측한다
INSERT INTO program_tags (program_id, tag)
SELECT id, t FROM ins, unnest(ARRAY['검사','엑셀','자동화']) AS t;

INSERT INTO program_tech (program_id, tech)
SELECT p.id, t
FROM programs p, unnest(ARRAY['Python','Go']) AS t
WHERE p.slug LIKE 'lt-prog-%'
  AND NOT EXISTS (SELECT 1 FROM program_tech x WHERE x.program_id = p.id);

INSERT INTO program_purposes (program_id, purpose)
SELECT p.id, u
FROM programs p, unnest(ARRAY['check','convert']) AS u
WHERE p.slug LIKE 'lt-prog-%'
  AND NOT EXISTS (SELECT 1 FROM program_purposes x WHERE x.program_id = p.id);

INSERT INTO program_run (program_id, run_type)
SELECT p.id, 'gitea'
FROM programs p
WHERE p.slug LIKE 'lt-prog-%'
  AND NOT EXISTS (SELECT 1 FROM program_run x WHERE x.program_id = p.id);

SELECT count(*) AS loadtest_programs FROM programs WHERE slug LIKE 'lt-prog-%';
