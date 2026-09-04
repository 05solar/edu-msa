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
([docs/MSA_SERVICE_SPEC.md](docs/architecture/MSA_SERVICE_SPEC.md))만 지키면 새로운 서비스로
등록·배포된다.

---

## 이 저장소의 구조

```
edu-msa/
├── README.md · AGENT.md       # 전체 개요 · 에이전트 규칙 (루트 문서는 이 둘만)
├── docs/
│   ├── architecture/          # ARCHITECTURE(설계) · MSA_SERVICE_SPEC(표준 규격)
│   ├── guides/                # VIBE_CODING_GUIDE (바이브 코더용 안내)
│   ├── operations/            # DEPLOY(배포) · SECURITY(보안 하드닝)
│   └── planning/              # ROADMAP · VERSIONS · PROCESS(이력) · GITEA_PLAN · BASE_SERVICES_PLAN
├── html/                      # 시각 문서(로컬 전용 — 원격 미추적)
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
├── examples/                  # 기본 업무 서비스 7개(카테고리별 1개, 각 폴더 = 배포 가능한 레포)
└── deploy/
    ├── docker-compose.yml     # postgres + auth-db + auth-service + traefik(서브도메인) + backend
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

역할은 `USER`(외부 사용자) · `CODER`(내부 직원) · `ADMIN`(운영 관리자) 세 가지다.
**회원가입은 항상 `USER`(최소 권한)로 생성**되고, 상향 권한(`CODER`/`ADMIN`)은 가입 시
`requestRole` 또는 로그인 후 마이페이지에서 **신청**만 접수된다. 실제 부여는 운영 관리자가
신청(`role-request`)을 승인해야 이뤄지므로, **자가 가입/신청만으로는 권한이 오르지 않는다.**
자세한 내용은 [auth-service/README.md](auth-service/README.md) 참고.

### 현재 구현된 인증 경로

`auth-service` 는 **인증 방식과 무관하게 JWT 를 발급하는 관문**으로 설계했다.

```
  [LOCAL]  회원가입 → ID/PW 로그인 ─┐
                                    ├─→ auth-service ─→ JWT 발급 ─→ 각 서비스가 자체 검증
  [DEMO]   데모 로그인 (비밀번호 없음) ┘
```

데모 로그인은 시연용 흐름이라 비밀번호를 입력하지 않지만, 발급되는 토큰은 일반 로그인과
같다. 따라서 데모로 진입해도 프로그램 등록·승인·배포 등 실제 API 를 그대로 사용할 수 있다.
좌측 하단의 권한 전환도 해당 역할의 데모 계정으로 토큰을 다시 받는 방식이라
서버가 판단하는 권한과 화면이 어긋나지 않는다.

데모 계정과 매핑, 비활성화 방법은 [auth-service/README.md](auth-service/README.md) 참고.

### 교육청 SSO 연동 (이번 범위 아님 · 추후 검토)

목표 아키텍처에는 교육청 홈페이지 사용자 정보를 SSO 로 연동하는 구성이 들어 있으나,
**이번 데모 범위에는 포함되지 않는다.** 현재 목표는 프로그램을 올리고 버전을 관리해
사용할 수 있는 플랫폼 데모를 완성하는 것이고, 인증은 그 데모가 돌아가는 데 필요한
수준(자체 ID/PW + 데모 로그인)까지만 만든다.

아래는 나중에 SSO 를 붙일 때를 위한 메모이며, 지금 구현된 것이 아니다.
자체 인증은 SSO 를 대체하는 것이 아니라 함께 쓰이는 경로로 본다.
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
> 근거해 병행하는 것으로 보고 구현했다. SSO 착수 시점에 확정하면 된다.

## 개발 단계 (Milestones)

| 단계 | 내용 | 상태 |
| --- | --- | --- |
| 1 | 저장소 스캐폴드 + 프론트엔드 데모 (7개 화면, 데모 로그인/권한 전환) | 완료 |
| 2 | Spring 백엔드 CRUD + PostgreSQL 연동 | 완료 |
| 3 | MSA 동적 배포 파이프라인 (GitHub 레포 → 새 서비스) + K8s 매니페스트 | 완료 |

### MSA 배포 파이프라인 (Phase 3)

GitHub 레포 등록 → `service.yaml`/`Dockerfile` 규격 검증 → 이미지 빌드 →
K8s 매니페스트(Deployment/Service/Ingress) 렌더링·적용 → 헬스 통과 → 공개.

- 표준 규격: [docs/MSA_SERVICE_SPEC.md](docs/architecture/MSA_SERVICE_SPEC.md)
- K8s 매니페스트: [deploy/k8s/](deploy/k8s/) (namespace·플랫폼·서비스 템플릿·RBAC)
- **기본 서비스 7개**: [examples/](examples/) — 교육청 업무 분야(category)별 **개인용 단발 도구** 1개씩.
  개인이 접속해 한 번의 작업(검사·변환·생성·계산·추출)을 처리하고 끝내는 도구이며(멀티유저 협업·상태
  관리 시스템 아님), 언어는 도구 특성에 맞게 선택했다.

  | 서비스 | 업무 분야 | 언어 | 접속(웹에서 바로 사용) |
  |---|---|---|---|
  | doc-proofreader | 공문서 오타·맞춤법 검사 | Go | `http://doc-proofreader.localhost` |
  | seat-maker | 학생 자리배치(엑셀 입출력) | Python | `http://seat-maker.localhost` |
  | timetable-checker | 시간표 충돌 검사·이미지 | TypeScript | `http://timetable-checker.localhost` |
  | travel-allowance | 국내출장 여비 계산 | C# | `http://travel-allowance.localhost` |
  | asset-label | 비품 QR 라벨 시트(PDF) | Java | `http://asset-label.localhost` |
  | data-summarizer | 표 데이터 통계·차트 | Python | `http://data-summarizer.localhost` |
  | doc-ocr | 문서 이미지 OCR 추출 | Python | `http://doc-ocr.localhost` |

  모두 비루트·`/healthz`·통일 오류포맷·개인 단발형(상태 저장·공유 없음). seed(`programs.json`)에 내부
  계정 소유로 등록 → 배포 시 `edu-services`. "웹에서 바로 사용"은 포트가 아니라 **서브도메인**
  (`http://<slug>.localhost`)으로 열린다(로컬은 Traefik 리버스 프록시가 Host 헤더로 라우팅).
  각 서비스·플랫폼에는 링크 미리보기(OG) 이미지와 파비콘이 포함된다.
- 백엔드 배포 API: `POST /api/deploy/validate`, `POST /api/programs/{id}/deploy`
- 배포 모드(`EDU_DEPLOY_MODE`): `simulate`(매니페스트 렌더만·기본) · `docker`(호스트 Docker로 **실제 컨테이너 기동**) · `real`(K8s `kubectl apply`)
- 레포 주소 형식: `https://github.com/…`(실제) · `local://examples/<slug>`(플랫폼 동봉 기본 서비스)
- 자세한 배포/모드는 [backend/README.md](backend/README.md)
- **K8s로 띄우는 법**: [deploy/k8s/README.md](deploy/k8s/README.md) — 로컬 `kind` 리허설로
  GitHub 레포의 서비스가 실제 **Pod + Service**로 떠서 응답하는 것까지 검증됨.

## 전체 스택 한 번에 실행

```bash
# 0) 시크릿 준비 — auth-service 발급/백엔드 검증 공용 서명 키(≥32B)
cp deploy/.env.example deploy/.env        # EDU_JWT_SECRET 등 값 채우기

# 1) 백엔드 스택 (Docker): postgres · auth-db · auth-service(:8089) · traefik(:80) · backend(:8088)
docker compose -f deploy/docker-compose.yml up --build -d

# 2) 프론트엔드 (백엔드 연동 모드)
cd frontend
npm install
npm run dev                               # http://localhost:5173
# VITE_USE_API 기본값은 true(백엔드 API 모드). false 로 두면 목업만으로 도는 오프라인 데모.
```

- 접속: **플랫폼** http://localhost:5173 (로그인 또는 데모 로그인 후 이용) ·
  **배포된 기본 서비스** `http://<slug>.localhost` (예: `http://doc-proofreader.localhost`).
- 프론트 개발 서버 프록시: `/api/auth` → auth-service(`localhost:8089`), `/api` → backend(`localhost:8088`).
- `EDU_JWT_SECRET` 미설정 시 compose 가 기동을 거부한다(의도된 안전장치). 배포 서비스는 Traefik(:80)이
  `<slug>.localhost` Host 로 각 컨테이너에 라우팅한다.

### Kubernetes 로 한 번에 (kind 로컬 / 실서버 겸용)

```bash
./deploy/bootstrap.sh up            # 또는:  make up   (kind 자동 생성 → 이미지 빌드/푸시 → 코어+운영스택)
# 접속: http://edu.localhost
```
실서버(GPU 박스 포함)·GPU 테넌트 설정은 **[DEPLOY.md](docs/operations/DEPLOY.md)** 참고.

## 문서 안내 (문서 지도)

| 문서 | 내용 |
| --- | --- |
| [DEPLOY.md](docs/operations/DEPLOY.md) | **원커맨드 배포 & GPU 서버 안내** (K8s 한 번에·실서버·GPU) |
| [VERSIONS.md](docs/planning/VERSIONS.md) | **버전 관리 & 고도화 이력** — 단계별 이력·태깅 규칙·Gitea 계획·백로그·진행 프로세스 |
| [docs/GITEA_PLAN.md](docs/planning/GITEA_PLAN.md) | 내부 Gitea 구축 상세 계획 — 6단계 작업·기간·검증 시나리오 |
| [deploy/PRODUCTION.md](deploy/PRODUCTION.md) | 실서버(k3s·Calico·레지스트리·도메인/TLS·CNPG·시크릿·GPU) 상세 가이드 |
| [docs/VIBE_CODING_GUIDE.md](docs/guides/VIBE_CODING_GUIDE.md) | 바이브 코더가 먼저 읽는 사람용 안내 |
| [docs/MSA_SERVICE_SPEC.md](docs/architecture/MSA_SERVICE_SPEC.md) | 표준 서비스 규격(기술 계약) |
| [docs/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) | 전체 아키텍처 설계 |
| `frontend/public/guides/AI_BUILD_SPEC.md` | **다운로드용 AI 지시서** — AI에 첨부해 규격대로 프로젝트 생성 (+ 파이썬/Node/정적 템플릿) |
| [examples/README.md](examples/README.md) | 업무 분야별 실동작 예제 목록·실행법 |
| [deploy/k8s/README.md](deploy/k8s/README.md) | K8s 매니페스트 구성·적용 순서 |
| [SECURITY.md](docs/operations/SECURITY.md) | 멀티테넌트 보안 하드닝(신뢰 등급·격리·검증) |
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

로그인 화면의 **데모로 시작하기** 버튼으로 계정 입력 없이 진입한다.
좌측 하단 "시연용 권한 전환"으로 외부 사용자 / 내부 직원 / 운영 관리자 역할을 바꿔
볼 수 있다. (실행 방법은 위 "전체 스택 한 번에 실행" 참고)

데모 진입도 `auth-service` 에서 실제 토큰을 발급받으므로, 등록·승인·배포 등
플랫폼 API 를 그대로 사용할 수 있다. 권한 전환 역시 해당 역할의 데모 계정으로
토큰을 다시 받는다.

아이디와 비밀번호로 로그인하려면 데모 계정을 쓰면 된다. 계정 목록과 공통 임시
비밀번호는 [auth-service/README.md](auth-service/README.md) 참고.

아이디 찾기·비밀번호 찾기는 화면과 입력값 검증까지 동작하며, 실제 조회와 메일 발송은
SMTP 연동 이후 완성된다.
