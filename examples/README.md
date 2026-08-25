# examples · 교육청 기본 서비스 (7개 · 7개 언어)

edu-msa 플랫폼에 기본 내장되는 **실제 업무용 서비스 7개**입니다. 각기 다른 교육청/학교 업무 분야를
서로 다른 프로그래밍 언어로 구현했으며, 단순 데모가 아니라 실제 업무 흐름(상태 전이·검증·집계 등)을
완결하는 수준으로 만들어졌습니다. 모두 서브에이전트 업무적합성 검증에서 "업무 사용 가능 수준" 판정을 받았습니다.

## 서비스 목록

| 서비스 | 업무 분야 | 구현 언어/프레임워크 | 주요 기능 | 배포 경로 |
|---|---|---|---|---|
| doc-approval | 공문/업무요청 결재 | Go / net/http | 기안→상신→결재선(검토/승인/전결)→승인/반려·재상신·감사이력 | `/svc/doc-approval` |
| facility-maint | 학교 시설 유지보수 | Python / FastAPI | 접수→배정→작업→완료, SLA·보류시간 제외·재오픈·비용집계 | `/svc/facility-maint` |
| staff-trip | 교직원 출장·복무 | Java / Javalin | 신청→승인→여비 자동정산→지급, 관내출장·반송 | `/svc/staff-trip` |
| civil-desk | 학생·학부모 민원 | TypeScript / Fastify | 접수→답변→종결, SLA·에스컬레이션·통지·개인정보 마스킹 | `/svc/civil-desk` |
| asset-mgr | 교육 기자재·자산 | C# / .NET | 등록→배치→수리→폐기 생애주기, 감가상각·재물조사·CSV | `/svc/asset-mgr` |
| safety-check | 학교 안전점검 | Rust / axum | 계획→수행→지적→조치→완료, FAIL 자동지적·점검주기·기한초과 | `/svc/safety-check` |
| report-hub | 통계/보고 자료 | Kotlin / Ktor | 수집→집계→승인→공개, 대상기관 제출율·가중평균·CSV | `/svc/report-hub` |

7개 업무 분야 상호 중복 없음, 7개 언어 중복 없음.

## 공통 규격
독립 실행 · 독립 Dockerfile · **비루트 실행** · `/healthz` · Kubernetes readiness/liveness 대응 ·
`PORT` 환경변수 · 통일 오류 응답(`{"error":{"code","message"}}`) · 입력 검증 · 로그 ·
샘플(seed) 데이터 · `DATA_FILE` 파일 영속성(선택) · KST 시간대 · `/svc/<slug>` 경로 대응 ·
`local://examples/<slug>` 로 local/docker/real 모드 배포 · PodSecurity(restricted, 비루트) 호환.

## 배포 방법
- **기본 서비스 Seed**: `backend/src/main/resources/seed/programs.json` 에 7개가 등록되어 있으며,
  소유자는 내부 계정(CODER/ADMIN)이라 배포 시 신뢰 네임스페이스 `edu-services` 로 배치됩니다.
- **소스**: `repo: local://examples/<slug>` (플랫폼에 동봉). docker 모드는 이 경로에서 빌드,
  real(K8s) 모드는 레지스트리 빌드(Kaniko) 후 배포합니다.
- **로컬 검증**:
  ```bash
  cd examples/<slug>
  docker build -t <slug> . && docker run -p 8080:8080 <slug>
  curl localhost:8080/healthz
  ```
- **플랫폼 docker 모드 검증**(예): 백엔드를 `EDU_DEPLOY_MODE=docker`로 기동 후
  `POST /api/programs/{id}/deploy` → 호스트 Docker에 `edu-svc-<slug>` 컨테이너 기동 → `/healthz 200`.

각 서비스 상세는 폴더별 `README.md` 참조. 재구성 계획·검증 현황은 [../docs/BASE_SERVICES_PLAN.md](../docs/BASE_SERVICES_PLAN.md).
