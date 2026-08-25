# civil-desk · 학생·학부모 민원 처리 (TypeScript/Node/Fastify)

## 대상 / 문제
학교·교육청에 접수되는 학생·학부모 민원을 접수부터 공식 답변·종결까지 추적한다.
채널(전화/방문/온라인/서면)·분야별로 분류하고, SLA 기한·에스컬레이션·만족도를 관리한다.

## 핵심 업무 흐름 / 상태
`RECEIVED`(접수) → `ASSIGNED`(배정) → `IN_PROGRESS`(처리) → `ANSWERED`(답변) → `CLOSED`(종결)
· `REJECTED`(반려). 종결 민원은 만족도 등록·재처리(재민원) 가능. 진행 중 민원은 에스컬레이션(상급 이관·기한 단축).

## 데이터 모델
Complaint: id, title, content, category(학사/급식/시설/교통/교권/기타), channel, complainant,
contact, anonymous, priority, status, assignee, dueDate(SLA), escalated, satisfaction, answers[], history[]

## 주요 API
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/healthz` | 상태 |
| GET | `/api/complaints?status=&category=&assignee=&priority=&q=&sort=&page=&size=` | 목록·필터·검색·정렬·페이지 |
| POST | `/api/complaints` | 접수(익명 가능; 비익명은 complainant 필수) |
| GET | `/api/complaints/{id}` | 상세(overdue 포함) |
| PATCH | `/api/complaints/{id}` | 수정(접수 상태) |
| POST | `/api/complaints/{id}/assign` | 담당 배정 |
| POST | `/api/complaints/{id}/start` | 처리 시작 |
| POST | `/api/complaints/{id}/answer` | 공식 답변 등록(복수 가능) → ANSWERED |
| POST | `/api/complaints/{id}/close` | 종결(답변 필요) |
| POST | `/api/complaints/{id}/reject` | 반려(reason 필수) |
| POST | `/api/complaints/{id}/escalate` | 에스컬레이션(상급 이관, 우선순위 1단계↑·기한 재산정) |
| POST | `/api/complaints/{id}/reopen` | 재개(종결→처리 재민원 / 반려→접수 번복) |
| POST | `/api/complaints/{id}/withdraw` | 취하(민원인 자진 철회) |
| POST | `/api/complaints/{id}/satisfaction` | 만족도(1~5, 종결건) |
| GET | `/api/complaints/{id}/history` | 처리 이력 |
| GET | `/api/stats` | 상태/분야 통계, overdue, 에스컬레이션 수, 평균 만족도 |

## 검증 규칙 / 예외
- title·content 필수, category/channel/priority 화이트리스트, 비익명 시 complainant 필수, title 150자.
- 전이 통제: 배정(접수/배정), 답변(배정/처리/답변), 종결(답변), 반려(접수/배정), 만족도/재처리(종결).
- 우선순위별 SLA(URGENT 24h/HIGH 72h/NORMAL 168h/LOW 336h), 초과 시 overdue. 잘못된 전이 409, 미존재 404, 입력오류 400.
- 오류 형식 `{"error":{"code","message"}}`.

## 회신 통지 / 개인정보
- **회신 통지**: 답변 등록 시 연락처가 있으면 통지 아웃박스(`notifications`, status QUEUED)에 적재.
  실제 SMS/메일 발송은 플랫폼 알림 서비스가 QUEUED 건을 처리하는 것을 전제로 한다.
- **개인정보**: 비익명 민원은 contact(연락처) 필수. 목록 응답의 contact는 마스킹, 상세(담당자)에서만 원본 노출.
  보존기간·파기는 플랫폼 정책 범위.

## 영속성 / 운영 경계
`DATA_FILE` 지정 시 JSON 보존. actor는 본문 값(플랫폼 게이트웨이 신원 주입 전제). KST. 비루트(uid 1001).
배포 경로 `/svc/civil-desk`. 향후: 실제 통지 발송 연동, 유사·반복민원 병합·집계, 민원인 자기조회, SLA 준수율 리포트.

## 실행
```bash
docker build -t civil-desk . && docker run -p 8080:8080 civil-desk
curl localhost:8080/healthz
curl -X POST localhost:8080/api/complaints -d '{"title":"급식 문의","content":"...","category":"급식","channel":"온라인","complainant":"학부모"}'
```
