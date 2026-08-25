# report-hub · 통계/보고 자료 관리 (Kotlin/Ktor)

## 대상 / 문제
교육청은 각 학교로부터 재학생 현황·급식 만족도·안전점검 실적 등 정기 통계를 취합해 집계·보고한다.
이 서비스는 보고 항목을 정의하고 기관별 제출을 수집해 **항목별 자동 집계(합계·평균·최소·최대)** 후
승인·공개까지의 흐름을 관리한다.

## 핵심 업무 흐름 / 상태
`DRAFT`(초안) → `COLLECTING`(수집) → `AGGREGATED`(집계마감) → `APPROVED`(승인) → `PUBLISHED`(공개).
집계/승인 단계에서 `COLLECTING`으로 수집 재개 가능. 같은 기관 재제출은 갱신(덮어쓰기).

## 데이터 모델
- Report: id, title, category(학사통계/급식/시설/예산/안전/연수), period, fields[], dueDate, status,
  submissions[], history[]
- Field: key, label · Submission: org, values{key:number}, submitter, submittedAt
- aggregate(응답): 항목별 {count, sum, avg, min, max}

## 주요 API
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/healthz` | 상태 |
| GET | `/api/reports?status=&category=&q=&page=&size=` | 목록·필터·검색·페이지 |
| POST | `/api/reports` | 보고 생성(fields 정의) |
| GET | `/api/reports/{id}` | 상세(aggregate 포함) |
| PATCH | `/api/reports/{id}` | 수정(초안) |
| POST | `/api/reports/{id}/open` | 수집 개시 |
| POST | `/api/reports/{id}/submit` | 기관 제출(org, values) — 항목 검증·같은 기관 갱신 |
| POST | `/api/reports/{id}/close` | 집계 마감(제출 필요) |
| POST | `/api/reports/{id}/reopen` | 수집 재개 |
| POST | `/api/reports/{id}/approve` | 승인 |
| POST | `/api/reports/{id}/publish` | 공개 |
| GET | `/api/reports/{id}/submissions` / `aggregate` / `history` | 제출·집계·이력 |
| GET | `/api/reports/{id}/export` | 상급 보고용 CSV(기관×항목) |
| GET | `/api/stats` | 상태/분류 통계, 총 제출 수 |

## 취합·집계 심화
- **대상 기관 명부**: `targetOrgs`로 취합 대상을 지정하면 응답에 `submissionRate`(제출율)·`missingOrgs`(미제출 기관) 산출.
- **필드 타입**: `type=sum`(합계형, 기본) / `type=avg`(평균·점수형). avg에 `weightKey` 지정 시 **가중평균**(예: 학교별 응답자수 가중) 산출.
- **값 범위 검증**: 필드 `min`/`max`로 제출값 범위 강제(예: 만족도 1~5). field `key` 중복 금지.
- **정정재공개**: 공개(PUBLISHED) 보고도 `reopen`으로 수집 재개해 정정 후 재공개 가능.

## 검증 규칙 / 예외
- title·category·period·fields 필수, category 화이트리스트, fields 최소 1개(key·label).
- submit: org 필수, values는 정의된 항목만·숫자만(미정의 키/비숫자 400).
- 전이 통제: 개시(초안), 제출(수집), 마감(수집·제출≥1), 승인(집계), 공개(승인), 재개(집계/승인).
- 잘못된 전이 409, 미존재 404, 입력오류 400. 오류 형식 `{"error":{"code","message"}}`.

## 영속성 / 운영 경계
`DATA_FILE` 지정 시 JSON 보존(실서버 볼륨 권장). actor는 본문 값(플랫폼 게이트웨이 신원 주입 전제).
KST. 비루트(uid 1001). 배포 경로 `/svc/report-hub`. 향후: 미제출 기관 독촉·마감 알림, 제출 검증규칙(범위/필수),
연도 대비 추이, CSV/Excel export, 표준 보고서 템플릿.

## 실행
```bash
docker build -t report-hub . && docker run -p 8080:8080 report-hub
curl localhost:8080/healthz
curl -X POST localhost:8080/api/reports -d '{"title":"재학생현황","category":"학사통계","period":"2026-2","fields":[{"key":"boys","label":"남"},{"key":"girls","label":"여"}]}'
```
