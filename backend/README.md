# 백엔드 · 교육청 코드 공유 플랫폼 API

Spring Boot 3 (Gradle Kotlin DSL, Java 21) + PostgreSQL. 프로그램 등록/조회/검토,
알림, 사용자 권한, 분류 체계, 배포 파이프라인 API를 제공한다. 인증은 별도의
[auth-service](../auth-service/README.md)가 담당하고, 이 서비스는 auth-service가 발급한
JWT를 **동일한 `EDU_JWT_SECRET`으로 자체 검증**해 인가만 판단한다(요청마다 auth-service 호출 없음).

## 실행 (권장: Docker)

로컬에 JDK/Gradle이 없어도 Docker만으로 빌드·실행할 수 있다. compose는
`db` + `auth-db` + `auth-service`(:8089) + `traefik`(:80) + `backend`(:8088)를 함께 띄운다.

```bash
# 저장소 루트에서 — deploy/.env 먼저 준비(EDU_JWT_SECRET≥32B, EDU_SEED_PASSWORD 등)
cd deploy && cp .env.example .env
docker compose -f docker-compose.yml up --build
# API: http://localhost:8088/api,  Postgres: localhost:5432
```

프론트 Vite(:5173) 개발 서버는 `/api/auth`→8089, `/api`→8088로 프록시한다.
**플랫폼은 로그인(또는 데모 로그인) 후 이용**하며, 프론트의 `VITE_USE_API` 기본값은
`true`(API 모드)다.

## 실행 (로컬에 JDK 21 + Gradle 있는 경우)

```bash
cd backend
gradle bootRun          # PostgreSQL이 localhost:5432 에 떠 있어야 함
```

환경변수: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `PORT`(기본 8080),
`CORS_ORIGINS`(기본 http://localhost:5173), `EDU_SEED`(기본 true),
**`EDU_JWT_SECRET`**(auth-service와 동일해야 함, 소스에 두지 않음).

## 패키지 구조 (기능별 분리)

```
com.edu.msa
├── EduMsaApplication.java
├── common/          # CORS·전역 예외 처리·API 오류 응답
├── security/        # JWT 자체 검증·인증 필터·RBAC(SecurityConfig)·whoami
├── catalog/         # 분류 체계(업무 분야/기능/제공방식/기술) 조회
├── program/         # 프로그램 도메인·CRUD·댓글
│   ├── domain/  repository/  dto/  ProgramService  ProgramController
├── review/          # 승인/반려/공개중지/재공개 + 처리 이력
├── notification/    # 알림
├── user/            # 사용자·권한 표시 데이터 (인가 판단은 JWT role 클레임 기준)
├── deploy/          # 배포 파이프라인·작업 큐·매니페스트 렌더
└── bootstrap/       # 최초 실행 시 시드 데이터 로드 (resources/seed/*.json)
```

## 보안 · 인가 (RBAC)

`com.edu.msa.security.SecurityConfig`가 STATELESS 필터 체인으로 모든 요청을 통과시키며,
`Authorization: Bearer` JWT의 `role` 클레임으로 접근을 제어한다.

| 경로 | 접근 |
| --- | --- |
| `GET /api/health`, `/api/healthz`, `/api/catalog/**`, `/actuator/**` | 공개 |
| `GET /api/programs`, `/api/programs/{id}`, 의견 등록 등 | 로그인 사용자 |
| `POST /api/programs` (프로그램 등록), `POST /api/deploy/validate` (레포 규격 정적 검증) | CODER 이상 |
| `/api/programs/all`, `/api/programs/pending`, `/api/programs/*/review`, `/api/review/logs`, `/api/programs/*/deploy`, `/api/deploy`, `/api/users`, `/api/users/*/role` | ADMIN |

계정·권한의 단일 소스는 auth-service다. 회원가입은 항상 최소 권한(USER)으로 만들어지고,
CODER/ADMIN 상향은 신청→운영 관리자 승인으로만 부여된다(자가 상승 불가). 자세한 흐름은
[auth-service/README.md](../auth-service/README.md) 참조.

## 주요 엔드포인트

| 메서드 | 경로 | 접근 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/health` | 공개 | 헬스 체크 |
| GET | `/api/catalog` | 공개 | 분류 체계 전체 |
| GET | `/api/programs` | 로그인 | 공개 프로그램 목록 (필터/정렬/검색) |
| GET | `/api/programs/{id}` | 로그인 | 상세 |
| POST | `/api/programs` | CODER+ | 등록 요청(pending) |
| POST | `/api/programs/{id}/comments` | 로그인 | 의견 등록 |
| GET | `/api/programs/all` | ADMIN | 전체(비공개 포함) 목록 |
| GET | `/api/programs/pending` | ADMIN | 검토 대기 목록 |
| POST | `/api/programs/{id}/review` | ADMIN | 승인/반려/중지/재개 |
| GET | `/api/review/logs` | ADMIN | 처리 이력 |
| GET | `/api/notifications?to=이름` | 로그인 | 내 알림 |
| POST | `/api/notifications/{id}/read` | 로그인 | 읽음 |
| GET | `/api/users` | ADMIN | 사용자 목록 |
| PATCH | `/api/users/{name}/role` | ADMIN | 권한 변경(표시용) |
| POST | `/api/deploy/validate` | CODER+ | 레포 표준 규격 정적 검증(빌드/배포 없음, 등록자 자가 점검) |
| POST | `/api/deploy` · `/api/programs/{id}/deploy` | ADMIN | 배포(작업 큐 적재) |
| GET | `/api/programs/{id}/deployment` | 로그인 | 최근 배포 상태 |

### 배포 파이프라인 (deploy 도메인)

`소스(레포/예제) → service.yaml/Dockerfile 검증 → 이미지 빌드 → 배포 → 공개`.
배포 요청은 즉시 실행하지 않고 `DeployJob` 큐에 적재(202)되어 `DeployWorker`가 처리한다.

**배포 모드 (`EDU_DEPLOY_MODE`)**

| 모드 | 동작 | 접속 |
| --- | --- | --- |
| `simulate` (기본) | 검증 + K8s 매니페스트 렌더만, 실제 실행 없음 | 데모·미리보기 |
| `docker` | 호스트 Docker로 **실제 이미지 빌드 + 컨테이너 기동** | `http://<slug>.localhost` (Traefik) |
| `real` | Kaniko 인클러스터 빌드 + `kubectl apply` (K8s) | 클러스터 ingress |

`docker` 모드는 컨테이너를 `eduproxy` 네트워크에 합류시키고 Traefik 동적 라우트
(`<slug>.localhost` → 컨테이너)를 기록한 뒤 컨테이너 `/healthz` 응답(readiness)까지
대기하고 running 으로 표시한다(첫 접속 502 방지). 포트가 아니라 **서브도메인**으로 접속한다.

**승인 시 자동 배포**: `edu.deploy.auto-on-approve=true`(기본)이면 운영 관리자가 승인할 때
배포 작업이 큐에 실려 컨테이너가 뜨고 프로그램이 자동 공개된다. (등록→승인→기동)

**레포 주소 형식**
- `https://github.com/…` : 실제 공개 레포 (git clone, docker/real 모드에서 빌드)
- `local://examples/<slug>` : compose에 마운트된 로컬 예제 (`examples/` → `/app/examples`, docker 모드 시험용)
- `sample://<name>` : classpath 번들 예제 (검증 전용, 빌드 컨텍스트 없음)

**로컬에서 실제 컨테이너 띄워보기**
```bash
# 저장소 루트에서 (Windows PowerShell: $env:EDU_DEPLOY_MODE="docker")
cd deploy && EDU_DEPLOY_MODE=docker docker compose up --build -d
# 로그인 토큰으로 ADMIN 배포 호출
curl -X POST localhost:8088/api/deploy -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <ADMIN JWT>' \
  -d '{"repoUrl":"local://examples/data-summarizer"}'
# 배포 완료 후 http://data-summarizer.localhost 로 접속 → 실제 배포된 컨테이너
```
compose는 호스트 `docker.sock`과 `examples/`를 백엔드에 마운트한다. 매니페스트 템플릿은
`resources/deploy-templates/service-template.yaml`, 규격은
[../docs/architecture/MSA_SERVICE_SPEC.md](../docs/architecture/MSA_SERVICE_SPEC.md), 다운로드용 AI 지시서는
`frontend/public/guides/`.

자세한 설계는 [DESIGN.md](DESIGN.md), 작업 규칙은 [AGENT.md](AGENT.md) 참조.
