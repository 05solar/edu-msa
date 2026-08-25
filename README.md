# 교육청 코드 공유 · 내부 프로그램 공유 플랫폼 (edu-msa)

![CI](https://github.com/05solar/edu-msa/actions/workflows/ci.yml/badge.svg)

교육청 구성원이 단기 교육에서 바이브 코딩으로 만든 프로그램을 GitHub 레포지토리로
올리면, 본 플랫폼이 해당 코드를 가져와 **하나의 MSA 서비스로 띄워** 다른 직원들이
바로 사용할 수 있게 하는 사내 포털이다.

- 프론트엔드: **React + Vite + TypeScript(TSX)**
- 백엔드: **Spring Boot 3 (Gradle Kotlin DSL)**
- 데이터베이스: **PostgreSQL**
- 오케스트레이션: **Kubernetes (MSA)**

바이브 코더가 어떤 언어로 만들든(파이썬, Node, Go 등), 표준 규격
([docs/MSA_SERVICE_SPEC.md](docs/MSA_SERVICE_SPEC.md))만 지키면 새로운 서비스로
등록·배포된다.

---

## 이 저장소의 구조

```
edu-msa/
├── README.md · PROCESS.md · AGENT.md       # 전체 개요 · 진행 이력 · 에이전트 규칙
├── docs/
│   ├── VIBE_CODING_GUIDE.md   # 바이브 코더용 안내 (사람이 읽는 가이드)
│   ├── MSA_SERVICE_SPEC.md    # 표준 서비스 규격 (기술 계약)
│   └── ARCHITECTURE.md        # 전체 아키텍처 설계
├── frontend/                  # React + Vite + TSX
│   ├── README·PROCESS·AGENT·DESIGN·TEST.md
│   ├── public/guides/         # 다운로드용 AI 빌드 지시서(AI_BUILD_SPEC + 스택 템플릿)
│   └── src/…
├── backend/                   # Spring Boot 3 (Java 21, Gradle Kotlin DSL)
│   ├── README·PROCESS·AGENT·DESIGN·TEST.md
│   └── src/main/java/com/edu/msa/…
├── examples/                  # 기본 업무 서비스 7개(7개 언어, 각 폴더 = 배포 가능한 레포)
└── deploy/
    ├── docker-compose.yml     # postgres + backend (docker 실배포 모드 지원)
    └── k8s/                   # namespace · 플랫폼 · 서비스 템플릿 · RBAC
```

## 개발 단계 (Milestones)

| 단계 | 내용 | 상태 |
| --- | --- | --- |
| 1 | 저장소 스캐폴드 + 프론트엔드 데모 (7개 화면, 데모 로그인/권한 전환) | 완료 |
| 2 | Spring 백엔드 CRUD + PostgreSQL 연동 | 완료 |
| 3 | MSA 동적 배포 파이프라인 (GitHub 레포 → 새 서비스) + K8s 매니페스트 | 완료 |

### MSA 배포 파이프라인 (Phase 3)

GitHub 레포 등록 → `service.yaml`/`Dockerfile` 규격 검증 → 이미지 빌드 →
K8s 매니페스트(Deployment/Service/Ingress) 렌더링·적용 → 헬스 통과 → 공개.

- 표준 규격: [docs/MSA_SERVICE_SPEC.md](docs/MSA_SERVICE_SPEC.md)
- K8s 매니페스트: [deploy/k8s/](deploy/k8s/) (namespace·플랫폼·서비스 템플릿·RBAC)
- **기본 서비스 7개**: [examples/](examples/) — 실제 교육청 업무를 **7개 서로 다른 언어**로 구현(단순 데모 아님).

  | 서비스 | 업무 분야 | 언어 | 배포 경로 |
  |---|---|---|---|
  | doc-approval | 공문/업무요청 결재 | Go | `/svc/doc-approval` |
  | facility-maint | 학교 시설 유지보수 | Python(FastAPI) | `/svc/facility-maint` |
  | staff-trip | 교직원 출장·복무 | Java(Javalin) | `/svc/staff-trip` |
  | civil-desk | 학생·학부모 민원 | TypeScript(Fastify) | `/svc/civil-desk` |
  | asset-mgr | 교육 기자재·자산 | C#(.NET) | `/svc/asset-mgr` |
  | safety-check | 학교 안전점검 | Rust(axum) | `/svc/safety-check` |
  | report-hub | 통계/보고 자료 | Kotlin(Ktor) | `/svc/report-hub` |

  모두 비루트·`/healthz`·통일 오류포맷·상태기계 워크플로. seed(`programs.json`)에 내부 계정 소유로 등록 → 배포 시 `edu-services`.
- 백엔드 배포 API: `POST /api/deploy/validate`, `POST /api/programs/{id}/deploy`
- 배포 모드(`EDU_DEPLOY_MODE`): `simulate`(매니페스트 렌더만·기본) · `docker`(호스트 Docker로 **실제 컨테이너 기동**) · `real`(K8s `kubectl apply`)
- 레포 주소 형식: `https://github.com/…`(실제) · `local://examples/<slug>`(플랫폼 동봉 기본 서비스)
- 자세한 배포/모드는 [backend/README.md](backend/README.md)
- **K8s로 띄우는 법**: [deploy/k8s/README.md](deploy/k8s/README.md) — 로컬 `kind` 리허설로
  GitHub 레포의 서비스가 실제 **Pod + Service**로 떠서 응답하는 것까지 검증됨.

## 전체 스택 한 번에 실행

```bash
# 백엔드 + PostgreSQL (Docker)
docker compose -f deploy/docker-compose.yml up --build -d   # 백엔드 http://localhost:8088

# 프론트엔드 (백엔드 연동 모드)
cd frontend
npm install
$env:VITE_USE_API="true"; npm run dev     # PowerShell 기준. http://localhost:5173
# (VITE_USE_API 미설정 시 목업 데이터로 동작하는 오프라인 데모)
```

프론트엔드 개발 서버는 `/api` 요청을 백엔드(기본 `localhost:8088`)로 프록시한다.

## 문서 안내 (문서 지도)

| 문서 | 내용 |
| --- | --- |
| [docs/VIBE_CODING_GUIDE.md](docs/VIBE_CODING_GUIDE.md) | 바이브 코더가 먼저 읽는 사람용 안내 |
| [docs/MSA_SERVICE_SPEC.md](docs/MSA_SERVICE_SPEC.md) | 표준 서비스 규격(기술 계약) |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 전체 아키텍처 설계 |
| `frontend/public/guides/AI_BUILD_SPEC.md` | **다운로드용 AI 지시서** — AI에 첨부해 규격대로 프로젝트 생성 (+ 파이썬/Node/정적 템플릿) |
| [examples/README.md](examples/README.md) | 업무 분야별 실동작 예제 목록·실행법 |
| [deploy/k8s/README.md](deploy/k8s/README.md) | K8s 매니페스트 구성·적용 순서 |
| [SECURITY.md](SECURITY.md) | 멀티테넌트 보안 하드닝(신뢰 등급·격리·검증) |
| [deploy/PROCESS.md](deploy/PROCESS.md) · [deploy/AGENT.md](deploy/AGENT.md) | 인프라 진행 이력 · 작업 규칙 |
| [frontend/README.md](frontend/README.md) · [backend/README.md](backend/README.md) | 각 앱 실행·구조·API |
| 각 폴더 `AGENT.md` · `DESIGN.md` · `TEST.md` · `PROCESS.md` | 작업 규칙 · 설계 원칙 · 테스트 · 진행 이력 |

> 메타 문서(README/PROCESS/AGENT/DESIGN/TEST)는 작업 시마다 갱신한다. ([AGENT.md](AGENT.md))

## 핵심 규칙 (전 팀 공통)

1. **이모지/이모티콘 사용 금지.** 모든 아이콘은 인라인 SVG 아이콘 세트를 사용한다.
   ([frontend/DESIGN.md](frontend/DESIGN.md) 참조)
2. **화면·기능 단위로 폴더/파일을 분리한다.** 프론트는 페이지 폴더마다
   `*.tsx`와 `*.css`를 같은 폴더에 둔다. 백엔드는 기능별로 패키지를 나눈다.
3. **메타 문서(README/PROCESS/AGENT/DESIGN/TEST md)는 하나의 작업을 수행할 때마다
   갱신한다.** ([AGENT.md](AGENT.md) 참조)

## 데모 로그인

인증(로그인/로그아웃/회원가입/아이디 찾기/비밀번호 찾기)은 화면만 존재하며 아직
미구현이다. 시연용 데모 계정으로 진입하고, 좌측 하단 "시연용 권한 전환"으로
외부 사용자 / 내부 직원 / 운영 관리자 역할을 바꿔 볼 수 있다. (실행 방법은 위
"전체 스택 한 번에 실행" 참고)
