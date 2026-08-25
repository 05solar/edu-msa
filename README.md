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
├── auth-service/              # 인증 마이크로서비스 (Spring Boot 3, 자체 DB)
│   ├── README.md
│   └── src/main/java/com/edu/auth/…
├── examples/                  # 업무 분야별 실동작 예제 8개(각 폴더 = 배포 가능한 레포)
└── deploy/
    ├── docker-compose.yml     # postgres + backend + auth-db + auth-service
    ├── .env.example           # 시크릿 주입 예시 (EDU_JWT_SECRET 등)
    └── k8s/                   # namespace · 플랫폼 · 인증 · 서비스 템플릿 · RBAC
```

## 인증 구조

계정 정보의 단일 소스는 `auth-service` 이며 전용 DB(`auth-db`)를 사용한다.
로그인 시 발급된 JWT 를 **각 서비스가 동일한 `EDU_JWT_SECRET` 으로 직접 검증**하므로,
인증이 필요한 요청마다 `auth-service` 를 호출하지 않는다.

```
        사용자 → Frontend
                   │
        ┌──────────┴──────────┐
   /api/auth/**            /api/**
        ▼                     ▼
   auth-service            backend
        │                     │
        ▼                     ▼
     auth-db             platform-db
```

역할은 `USER`(외부 사용자) · `CODER`(내부 직원) · `ADMIN`(운영 관리자) 세 가지이며,
회원가입 기본값은 `USER` 이고 나머지는 운영 관리자가 부여한다.
자세한 내용은 [auth-service/README.md](auth-service/README.md) 참고.

### 현재 구현된 인증 경로

`auth-service` 는 **인증 방식과 무관하게 JWT 를 발급하는 관문**으로 설계했다.
지금은 자체 ID/PW 인증(LOCAL)만 구현되어 있다.

```
  [LOCAL]  회원가입 → ID/PW 로그인 ─┐
                                    ├─→ auth-service ─→ JWT 발급 ─→ 각 서비스가 자체 검증
  [DEMO]   데모 로그인 (프론트 전용) ┘   (거치지 않음)
```

데모 로그인은 시연용 흐름이라 서버를 거치지 않고 프론트엔드 상태만 바꾼다.
JWT 를 발급받지 않으므로 실제 보호 API 를 호출하지 않는다.

### 향후 교육청 SSO 연동 (미구현)

목표 아키텍처는 교육청 홈페이지 사용자 정보를 SSO 로 연동하는 구조다.
이번 단계에서 만든 자체 인증은 **SSO 를 대체하는 것이 아니라 함께 쓰이는 경로**로 본다.
외부 사용자는 교육청 계정이 없으므로 자체 회원가입이 계속 필요하기 때문이다.

```
  [SSO]    교육청 SSO → 직원 정보 확인 ─┐
  [LOCAL]  회원가입 → ID/PW 로그인 ─────┼─→ auth-service ─→ JWT 발급
  [DEMO]   데모 로그인 (프론트 전용) ────┘   (거치지 않음)
```

인증 경로와 역할의 대응은 다음을 기준으로 한다.

| 사용자 | 인증 경로 | 역할 |
| --- | --- | --- |
| 외부 사용자 | 자체 회원가입 + ID/PW | `USER` |
| 교육청 내부 직원 | 교육청 SSO | `CODER` |
| 운영 관리자 | 교육청 SSO + 권한 부여 | `ADMIN` |
| 시연 | 데모 로그인 | 전환하며 확인 |

**JWT 와 그 이후 구간은 그대로 재사용된다.** 인증 경로가 늘어나도 발급되는 토큰의
형태는 같으므로, 플랫폼 `backend` 를 포함한 자원 서버는 수정할 필요가 없다.

SSO 를 붙일 때 `auth-service` 에서 필요한 변경은 다음과 같다.

- `accounts` 에 인증 출처(`auth_provider`: `LOCAL` / `SSO`)와
  외부 식별값(`external_subject`) 컬럼 추가
- `password_hash` 를 nullable 로 변경 (SSO 계정은 비밀번호를 보관하지 않는다)
- SSO 콜백 처리와 외부 사용자 ↔ 계정 매핑, 최초 로그인 시 계정 자동 생성
- 직원 정보 기준으로 `CODER` 를 부여하는 규칙

인증을 기존 `backend` 에 넣지 않고 별도 서비스로 분리한 이유가 여기에 있다.
SSO 가 추가되어도 변경 범위가 `auth-service` 안에 갇힌다.

> 확인 필요: 자체 ID/PW 인증을 **외부 사용자용으로 계속 병행**할지, 아니면 SSO 도입 시
> **전면 교체**할지에 따라 위 설계가 달라진다. 현재는 역할 정의(`USER` = 외부 사용자)에
> 근거해 병행하는 것으로 보고 구현했다.

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
- 배포 모드(`EDU_DEPLOY_MODE`): `simulate`(매니페스트 렌더만·기본) · `docker`(호스트 Docker로 **실제 컨테이너 기동**) · `real`(K8s `kubectl apply`)
- 레포 주소 형식: `https://github.com/…`(실제) · `local:///workspace/examples/<name>`(로컬 예제) · `sample://travel-settlement`(검증 전용)
- 자세한 배포/모드는 [backend/README.md](backend/README.md), K8s는 [deploy/k8s/README.md](deploy/k8s/README.md)

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
