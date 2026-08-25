# facility-maint · 학교 시설 유지보수 관리 (Python/FastAPI)

## 대상 / 문제
학교의 누수·전기·냉난방 등 시설 고장은 접수되어 담당자에게 배정되고 작업 후 완료 처리된다.
이 서비스는 그 정비요청(Work Order)의 흐름을 상태 전이로 관리하고 우선순위별 SLA 기한과 통계를 제공한다.

## 핵심 업무 흐름 / 상태
`RECEIVED`(접수) → `ASSIGNED`(배정) → `IN_PROGRESS`(작업중) → `DONE`(완료)
· `ON_HOLD`(보류, 재개 시 이전 상태로) · `REJECTED`(반려). 접수/보류 문서는 수정 가능.
우선순위(URGENT/HIGH/NORMAL/LOW)에 따라 SLA 기한(4/24/72/168h)이 자동 산정되고 초과 시 overdue.

## 데이터 모델
Order: id, title, location, category(전기/배관/냉난방/목공/도장/기타), description, requester,
department, priority, status, assignee, dueDate, cost, completionNote, history[]

## 주요 API
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/healthz` | 상태 |
| GET | `/api/orders?status=&category=&priority=&assignee=&q=&page=&size=` | 목록·필터·검색·페이지 |
| POST | `/api/orders` | 접수(요청 등록) |
| GET | `/api/orders/{id}` | 상세(overdue 포함) |
| PATCH | `/api/orders/{id}` | 수정(접수/보류 상태) |
| POST | `/api/orders/{id}/assign` | 담당자 배정 |
| POST | `/api/orders/{id}/start` | 작업 시작 |
| POST | `/api/orders/{id}/hold` / `resume` | 보류 / 재개 |
| POST | `/api/orders/{id}/complete` | 완료(completionNote 필수, cost 선택) |
| POST | `/api/orders/{id}/reject` | 반려(reason 필수) |
| POST | `/api/orders/{id}/reopen` | 재오픈(완료건 하자/재발, reason 필수) |
| GET | `/api/orders/{id}/history` | 처리 이력 |
| GET | `/api/stats` | 상태/분류/우선순위 통계, overdue, **비용 집계(totalCost·costByCategory)** |

- 목록 정렬: `sort=id|due|priority` (기본 id 내림차순, due=기한 임박순, priority=우선순위순).
- 첨부: `attachments`(파일명/참조 목록, 접수·수정 시). 실제 바이너리 저장은 플랫폼/오브젝트스토리지 책임(메타만 관리).

## 검증 규칙 / 예외
- title·requester·location 필수, category/priority 화이트리스트, title 120자.
- 상태별 허용 전이만: 배정(RECEIVED/ASSIGNED), 시작(ASSIGNED·담당자 필요), 보류(ASSIGNED/IN_PROGRESS),
  완료(IN_PROGRESS), 반려(작업중/완료 불가). 잘못된 전이 409, 미존재 404, 입력오류 400.
- 오류 응답: `{"error":{"code","message"}}`.

## SLA 정합성
- 우선순위 변경(PATCH) 시 dueDate 자동 재산정. 보류(hold)~재개(resume) 대기시간은 dueDate에서 제외(연장).
- 재오픈 시 기한 재산정.

## 영속성 / 운영 경계 / 향후
- `DATA_FILE` 지정 시 JSON 파일로 보존(볼륨/emptyDir). actor는 본문 값(플랫폼 게이트웨이 신원 주입 전제).
- 배포 경로 `/svc/facility-maint`. 비루트(uid 1001) 실행.
- 향후 업무 확장: 예방정비(PM) 정기점검 스케줄, 배정/기한임박 알림, 담당업체 마스터,
  업무시간·공휴일 반영 SLA, 첨부 바이너리 저장 연동.

## 실행
```bash
docker build -t facility-maint . && docker run -p 8080:8080 facility-maint
curl localhost:8080/healthz
curl -X POST localhost:8080/api/orders -d '{"title":"...","requester":"김도현","location":"본관/2F","category":"전기","priority":"HIGH"}'
```
