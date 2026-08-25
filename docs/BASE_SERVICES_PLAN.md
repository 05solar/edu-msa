# 기본 서비스 재구성 계획 (7개 업무 서비스)

교육청 실제 업무에 활용 가능한 수준의 기본 서비스 7개를, 서로 다른 업무 분야 × 서로 다른 언어로
구현한다. 기존 examples/의 단순 데모 8종은 정리하고 아래 7종만 남긴다.

## 기존 정리 대상 (전부 삭제 → 대체)
budget-rate, civil-reply, class-hours, data-summary, doc-formatter, facility-check,
sample-service(travel-settlement), score-stats — 모두 Python 70~100줄 단순 계산/데모.
함께 정리: seed/programs.json 참조, deploy-samples/travel-settlement, README/PROCESS/문서 참조.

## 확정된 7개 서비스

| # | slug | 업무 분야 | 언어 / 프레임워크 | 핵심 업무 흐름 |
|---|------|----------|-------------------|----------------|
| 1 | doc-approval | 공문/업무요청 결재 | Go / net/http | 기안→상신→검토→승인/반려, 결재선·감사이력 |
| 2 | facility-maint | 학교 시설 유지보수 | Python / FastAPI | 접수→분류→배정→작업→완료, 우선순위·SLA |
| 3 | staff-trip | 교직원 출장·복무 | Java / Javalin | 출장신청→승인→정산(여비 계산)→지급 |
| 4 | civil-desk | 학생·학부모 민원 | TypeScript / Fastify | 접수→분류→배정→답변→종결, 기한·에스컬레이션 |
| 5 | asset-mgr | 교육 기자재·자산 | C# / .NET minimal API | 등록→불출→이관→수리→폐기, 감가·재물조사 |
| 6 | safety-check | 학교 안전점검 | Rust / axum | 점검계획→수행→지적사항→개선조치→완료 |
| 7 | report-hub | 통계/보고 자료 | Kotlin / Ktor | 자료수집→집계→보고서 생성→승인→공개 |

- 업무 분야 7개 상호 중복 없음. 구현 언어 7개 중복 없음.
- 각 서비스는 실제 업무 흐름(상태 전이)을 완결하며 단순 CRUD가 아님.

## 공통 규격 (모든 서비스)
독립 실행 · 독립 Dockerfile · 비루트 실행 · `/healthz` · readiness/liveness 대응 ·
환경변수(PORT) 설정 · 통일된 오류 응답 · 입력 검증 · 로그 · 샘플(seed) 데이터 ·
README · `/svc/<slug>` 경로 대응 · `local://examples/<slug>` 로 local/docker/real 모드 사용 ·
PodSecurity/securityContext(restricted, 비루트) 호환.

## 진행 방식
서비스 1개씩 순차: 분석 → 설계 → 구현 → docker build/run → API 테스트(정상/오류/미존재/상태전이) →
서브에이전트 업무적합성 검증 → 미흡 시 재개발 → 통과 시 Seed 등록 → 다음 서비스.

## 검증 상태 기록
(각 서비스 완료 시 아래에 결과를 갱신한다.)

| 서비스 | build | /healthz | 핵심 API | 서브에이전트 | Seed |
|---|---|---|---|---|---|
| doc-approval | ✅ Go/distroless | ✅ 200 | ✅ 상태전이·403/409/400/404·검색·통계·전결·재상신 | ✅ 업무 사용 가능(고도화 반영) | 대기(마지막 일괄) |
| facility-maint | ✅ Python/FastAPI | ✅ 200 | ✅ 상태전이·SLA·보류/재개·재오픈·비용집계·정렬·첨부 | ✅ 업무 사용 가능(고도화 반영) | 대기(마지막 일괄) |
| staff-trip | ✅ Java/Javalin | ✅ 200 | ✅ 여비 자동계산·승인/정산/지급·반려/취소/반송·관내출장·무료숙박 | ✅ 업무 사용 가능(고도화 반영) | 대기(마지막 일괄) |
| civil-desk | ✅ TS/Fastify | ✅ 200 | ✅ 접수·배정·답변·종결·반려·에스컬레이션·재개/취하·만족도·통지·마스킹 | ✅ 업무 사용 가능(고도화 반영) | 대기(마지막 일괄) |
| asset-mgr | - | - | - | - | - |
| safety-check | - | - | - | - | - |
| report-hub | - | - | - | - | - |
