# safety-check · 학교 안전점검 (Rust/axum)

## 대상 / 문제
학교의 소방·전기·가스·승강기·석면·급식위생 등 정기 안전점검은 계획 수립 → 현장 점검(체크리스트) →
지적사항(결함) 등록 → 개선조치 → 완료의 절차를 거친다. 이 서비스는 그 흐름을 상태 전이로 관리하고,
FAIL 항목의 지적사항 자동 생성과 미조치 지적의 완료 차단으로 안전관리 누락을 방지한다.

## 핵심 업무 흐름 / 상태
점검: `PLANNED`(계획) → `IN_PROGRESS`(수행) → `COMPLETED`(완료) · `CANCELED`(취소).
지적사항(finding): `OPEN`(미조치) → `RESOLVED`(조치완료). **미조치 지적이 있으면 점검 완료 불가.**

## 데이터 모델
- Inspection: id, title, type(소방/전기/가스/승강기/석면/시설/급식위생), area, scheduledDate, inspector,
  status, items[](체크리스트), findings[](지적사항), history[]
- Item: code, label, result(PASS/FAIL/NA), note
- Finding: id, description, severity(경미/중대/심각), status, dueDate, assignee, resolution, resolvedAt

## 주요 API
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/healthz` | 상태 |
| GET | `/api/inspections?status=&type=&area=&inspector=&q=&page=&size=` | 목록·필터·검색·페이지 |
| POST | `/api/inspections` | 점검 계획 수립(체크리스트 items 포함 가능) |
| GET | `/api/inspections/{id}` | 상세(openFindings 포함) |
| PATCH | `/api/inspections/{id}` | 수정(계획 상태) |
| POST | `/api/inspections/{id}/start` | 점검 시작 |
| POST | `/api/inspections/{id}/check` | 항목 판정(code, result). FAIL이면 지적 자동 생성 |
| POST | `/api/inspections/{id}/findings` | 지적사항 수동 등록(severity/dueDate) |
| POST | `/api/inspections/{id}/findings/{fid}/resolve` | 개선조치(resolution 필수) |
| POST | `/api/inspections/{id}/findings/{fid}/reopen` | 지적 재개(재발 시, RESOLVED→OPEN) |
| POST | `/api/inspections/{id}/complete` | 완료(미조치 지적 없어야 함, 차기 점검일 자동 산정) |
| POST | `/api/inspections/{id}/cancel` | 취소 |
| GET | `/api/inspections/{id}/history` | 이력 |
| GET | `/api/stats` | 상태/유형/심각도 통계, 미조치·조치 지적 수 |

## 검증 규칙 / 예외
- title·type·area 필수, type/severity/result 화이트리스트, scheduledDate 형식.
- 전이 통제: 시작/수정(계획), 항목판정·지적등록(진행), 조치(지적 OPEN), 완료(진행·미조치 지적 0).
- FAIL 항목은 동일 항목 OPEN 지적이 없을 때만 자동 생성(중복 방지). 잘못된 전이 409, 미존재 404, 입력 400.
- 오류 형식 `{"error":{"code","message"}}`.

## 법정 주기 / 기한 / 템플릿 / 증빙
- **점검주기**: `cycleMonths`(미지정 시 유형별 기본: 소방6·전기12·가스6·승강기1·석면12·시설6·급식위생3). 완료 시 `nextInspectionDate` 자동 산정, 도래 시 `inspectionOverdue`/통계 `dueInspections`.
- **조치기한**: 지적 `dueDate` 초과+미조치면 응답의 finding `overdue=true`, 통계 `overdueFindings` 집계.
- **체크리스트 템플릿**: items 미지정 시 유형별 표준 항목 자동 구성.
- **증빙**: 지적 `attachments`(사진/문서 참조 메타). 실제 바이너리 저장은 플랫폼 책임.

## 영속성 / 운영 경계
`DATA_FILE` 지정 시 JSON 보존(실서버는 볼륨 마운트 권장). actor는 본문 값(플랫폼 게이트웨이 신원 주입 전제).
KST. 비루트(uid 1001). 배포 경로 `/svc/safety-check`. 향후: 점검표 템플릿 관리, 정기점검 스케줄·법정주기 알림, 사진 증빙, 개선조치 기한 초과 알림.

## 실행
```bash
docker build -t safety-check . && docker run -p 8080:8080 safety-check
curl localhost:8080/healthz
curl -X POST localhost:8080/api/inspections -d '{"title":"소방점검","type":"소방","area":"본관","items":[{"code":"F1","label":"소화기"}]}'
```
