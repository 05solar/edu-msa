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
├── README.md                 # 이 문서 (전체 개요)
├── PROCESS.md                # 전체 작업 프로세스·진행 이력
├── AGENT.md                  # AI 에이전트 작업 규칙 (md 리뉴얼 규칙 포함)
├── docs/
│   ├── VIBE_CODING_GUIDE.md  # 바이브 코더용 가이드 (가장 먼저 읽는 문서)
│   ├── MSA_SERVICE_SPEC.md   # "기본 프로그램은 이래야 한다" 표준 서비스 규격
│   └── ARCHITECTURE.md       # 전체 아키텍처 설계
├── frontend/                 # React + Vite + TSX
│   ├── README.md · PROCESS.md · AGENT.md · DESIGN.md · TEST.md
│   └── src/…
├── backend/                  # Spring Boot 3 (Gradle Kotlin DSL)  ← Phase 2
│   ├── README.md · PROCESS.md · AGENT.md · DESIGN.md · TEST.md
│   └── src/…
└── deploy/                   # docker-compose · k8s 매니페스트       ← Phase 3
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
- 표준 예제 서비스: [examples/](examples/) — 업무 분야별로 **실제 동작하는 프로그램 7개**
  (문서/학생/교육과정/예산/시설/데이터/민원) + 최소 예제 `sample-service`. 모두 `docker build` 후 즉시 실행.
- 백엔드 배포 API: `POST /api/deploy/validate`, `POST /api/programs/{id}/deploy`
- 실행 모드: `EDU_DEPLOY_MODE=simulate`(매니페스트 렌더만) 또는 `real`(docker/kubectl 실행)
- 오프라인 시험: 레포 주소에 `sample://travel-settlement` 사용

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
일반 사용자 / 바이브 코더 / 운영 관리자 역할을 바꿔 볼 수 있다.

## 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```
