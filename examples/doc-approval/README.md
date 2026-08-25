# doc-approval · 공문/업무요청 결재 워크플로 (Go)

## 대상 / 문제
교육청·학교 행정에서 공문·품의·업무요청은 **기안 → 상신 → 결재선(검토/승인) → 승인/반려**의
전자결재 흐름을 탄다. 이 서비스는 그 흐름을 상태 전이로 관리하고 감사이력·통계를 제공한다.

## 핵심 업무 흐름 / 상태
`DRAFT`(기안) → `IN_REVIEW`(상신·결재 진행) → `APPROVED`(최종승인) / `REJECTED`(반려)
· 기안자는 종결 전 `WITHDRAWN`(회수) 가능. 결재선은 순서가 있고, 각 단계는 지정된 결재자만 처리.

## 데이터 모델
- Document: id, title, docType(공문/업무요청/품의), drafter, department, content, priority, status, line(결재선), currentStep, dueDate, audit[]
- Step: order, approver, role(검토/승인/전결), status, comment, decidedAt
- Audit: at, actor, act, memo

## 주요 API
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/healthz` | 상태 |
| GET | `/api/documents?status=&q=&page=&size=` | 목록·필터·검색·페이지네이션 |
| POST | `/api/documents` | 기안 등록(approvers=결재선, roles=역할 검토/승인/전결) |
| PATCH | `/api/documents/{id}` | 기안/반려/회수 문서 수정 → DRAFT 복귀(재상신 가능) |
| GET | `/api/documents/{id}` | 상세 |
| POST | `/api/documents/{id}/submit` | 상신(DRAFT→IN_REVIEW, 결재선 필요) |
| POST | `/api/documents/{id}/approve` | 승인(본문 `actor`=현재 결재자, comment 선택) |
| POST | `/api/documents/{id}/reject` | 반려(comment 필수) |
| POST | `/api/documents/{id}/withdraw` | 회수(기안자만) |
| GET | `/api/documents/{id}/audit` | 감사이력 |
| GET | `/api/stats` | 상태/부서별 통계 |

## 검증 규칙 / 예외
- title·drafter 필수, docType 화이트리스트, title 120자 제한.
- DRAFT만 상신, 결재선 없으면 상신 불가(409).
- 결재 진행 중 문서만 승인/반려, **현재 단계 결재자만** 처리(403), 반려는 사유 필수.
- 종결(APPROVED/REJECTED) 문서는 회수 불가. 잘못된 전이는 409, 미존재는 404, 입력오류는 400.
- 오류 응답 형식: `{"error":{"code","message"}}`.

## 실행
```bash
docker build -t doc-approval . && docker run -p 8080:8080 doc-approval
curl localhost:8080/healthz
curl -X POST localhost:8080/api/documents -d '{"title":"...","drafter":"김도현","approvers":["박서준","정우성"]}'
curl -X POST localhost:8080/api/documents/1/approve -d '{"actor":"박서준","comment":"검토완료"}'
```
샘플 문서 3건(진행 중/승인/기안)이 시드되어 있다. 배포 경로: `/svc/doc-approval`.

## 상태 전이(막다른 상태 없음)
`DRAFT ⇄ 수정` · `DRAFT →상신→ IN_REVIEW` · `IN_REVIEW →승인→ (다음 단계|APPROVED)` ·
`IN_REVIEW →반려→ REJECTED →수정→ DRAFT →재상신` · `회수→ WITHDRAWN →수정→ DRAFT`.
전결(role=전결)이면 잔여 단계를 SKIPPED 처리하고 즉시 APPROVED.

## 영속성
`DATA_FILE` 환경변수를 주면 해당 경로에 JSON으로 저장/로드한다(볼륨/emptyDir 마운트 시 재시작에도 보존).
미지정 시 인메모리(시드)로 동작. 원자적 저장(tmp→rename).

## 운영 경계(플랫폼 책임)
- **신원/인증**: `actor`는 요청 본문 값이다. 실서비스에선 플랫폼 게이트웨이(SSO/토큰)가 검증된
  사용자 신원을 주입하는 것을 전제로 한다(서비스 자체 인증은 범위 밖).
- **경로 프리픽스**: `/svc/doc-approval` → 게이트웨이가 `/`로 rewrite하는 전제(플랫폼 Ingress).
- **향후 업무 확장**: 문서번호 자동채번, 첨부파일, 합의/대결, 지연(overdue) 알림, 부서별 조회 스코프.

