# staff-trip · 교직원 출장·복무 관리 (Java/Javalin)

## 대상 / 문제
교직원의 출장은 신청 → 관리자 승인 → 출장 수행 → 여비 정산 → 지급의 복무 절차를 거친다.
이 서비스는 그 흐름을 상태 전이로 관리하고, 여비를 규정에 따라 자동 계산한다.

## 핵심 업무 흐름 / 상태
`REQUESTED`(신청) → `APPROVED`(승인) → `SETTLED`(정산) → `PAID`(지급)
· `REJECTED`(반려, 수정 후 재신청) · `CANCELED`(신청/승인 건 취소). 신청/반려 건은 수정 가능.

## 여비 자동 계산(간이 규정)
- 일비 25,000/일 + 식비 25,000/일 + 숙박비(박수 × 70,000 상한, 정산 시 실비 override)
- 운임: 자가용 = 거리(km) × 262원 / 대중교통 = 실비(fare) / 관용차 = 0
- `total = 일비 + 식비 + 숙박 + 운임`. 신청 시 추정, 정산 시 실비 반영해 확정.
- **관내출장(tripType=관내)**: 정액(20,000/일), 식비·숙박 없음(운임만 별도).
- **무료숙박**: 정산 시 `lodgingActual: 0` 명시로 0원 확정 가능(관사·주최측 제공).

## 데이터 모델
Trip: id, applicant, department, rank(교사/부장/교감/교장/주무관/사무관), purpose, destination,
startDate, endDate, days, transport, distanceKm, fare, lodgingActual, status, approver,
expense{perDiem,meal,lodging,fare,total}, history[]

## 주요 API
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/healthz` | 상태 |
| GET | `/api/trips?status=&applicant=&department=&q=&page=&size=` | 목록·필터·검색·페이지 |
| POST | `/api/trips` | 출장 신청(날짜/직급/교통수단 검증, 여비 추정) |
| GET | `/api/trips/{id}` | 상세 |
| PATCH | `/api/trips/{id}` | 수정(신청/반려 상태, 여비 재계산) |
| POST | `/api/trips/{id}/approve` | 승인(actor 필수) |
| POST | `/api/trips/{id}/reject` | 반려(reason 필수) |
| POST | `/api/trips/{id}/cancel` | 취소(신청/승인 건) |
| POST | `/api/trips/{id}/settle` | 정산(fare/lodgingActual 실비, 여비 확정) |
| POST | `/api/trips/{id}/pay` | 지급 |
| POST | `/api/trips/{id}/return` | 반송(승인→신청 / 정산→승인, 정정용) |
| GET | `/api/trips/{id}/history` | 복무 이력 |
| GET | `/api/stats` | 상태/부서별 통계, 정산·지급 여비 합계 |

## 검증 규칙 / 예외
- applicant·purpose·startDate·endDate 필수, endDate≥startDate, rank/transport 화이트리스트, 금액 0 이상.
- 전이 통제: 승인/반려(REQUESTED), 취소(REQUESTED/APPROVED), 정산(APPROVED), 지급(SETTLED)만.
- 잘못된 전이 409, 미존재 404, 입력오류 400. 오류 형식 `{"error":{"code","message"}}`.

## 영속성 / 운영 경계
`DATA_FILE` 지정 시 JSON 보존. actor는 본문 값(플랫폼 게이트웨이 신원 주입 전제). KST. 비루트(uid 1001).
배포 경로 `/svc/staff-trip`. 향후: 결재선(다단계 승인), 복명서 첨부, 예산 연동, 공무원 여비 규정 상세화.

## 실행
```bash
docker build -t staff-trip . && docker run -p 8080:8080 staff-trip
curl localhost:8080/healthz
curl -X POST localhost:8080/api/trips -d '{"applicant":"김도현","purpose":"연수","destination":"세종","startDate":"2026-09-03","endDate":"2026-09-04","transport":"대중교통","fare":38000}'
```
