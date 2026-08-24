# 백엔드 · 교육청 코드 공유 플랫폼 API

Spring Boot 3 (Gradle Kotlin DSL, Java 21) + PostgreSQL. 프로그램 등록/조회/승인,
알림, 사용자 권한, 분류 체계 API를 제공한다.

## 실행 (권장: Docker)

로컬에 JDK/Gradle이 없어도 Docker만으로 빌드·실행할 수 있다.

```bash
# 저장소 루트에서
docker compose -f deploy/docker-compose.yml up --build
# API: http://localhost:8080/api,  Postgres: localhost:5432
```

## 실행 (로컬에 JDK 21 + Gradle 있는 경우)

```bash
cd backend
gradle bootRun          # PostgreSQL이 localhost:5432 에 떠 있어야 함
```

환경변수: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `PORT`(기본 8080),
`CORS_ORIGINS`(기본 http://localhost:5173), `EDU_SEED`(기본 true).

## 패키지 구조 (기능별 분리)

```
com.edu.msa
├── EduMsaApplication.java
├── common/          # CORS·전역 예외 처리·API 오류 응답
├── catalog/         # 분류 체계(업무 분야/기능/제공방식/기술) 조회
├── program/         # 프로그램 도메인·CRUD·댓글
│   ├── domain/  repository/  dto/  ProgramService  ProgramController
├── review/          # 승인/반려/공개중지/재공개 + 처리 이력
├── notification/    # 알림
├── user/            # 사용자·권한 (데모, 실제 인증 미구현)
└── bootstrap/       # 최초 실행 시 목업 데이터 시드 (resources/seed/*.json)
```

## 주요 엔드포인트

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/health` | 헬스 체크 |
| GET | `/api/catalog` | 분류 체계 전체 |
| GET | `/api/programs` | 공개 프로그램 목록 (필터/정렬/검색) |
| GET | `/api/programs/{id}` | 상세 |
| POST | `/api/programs` | 등록 요청(pending) |
| POST | `/api/programs/{id}/comments` | 의견 등록 |
| GET | `/api/programs/pending` | 검토 대기 목록(운영) |
| POST | `/api/programs/{id}/review` | 승인/반려/중지/재개 |
| GET | `/api/notifications?to=이름` | 내 알림 |
| POST | `/api/notifications/{id}/read` | 읽음 |
| POST | `/api/notifications/read-all?to=이름` | 모두 읽음 |
| GET | `/api/users` | 사용자 목록 |
| PATCH | `/api/users/{name}/role` | 권한 변경 |
| POST | `/api/deploy/validate` | 레포 표준 규격 검증 |
| POST | `/api/programs/{id}/deploy` | 프로그램 배포(파이프라인 실행) |
| GET | `/api/programs/{id}/deployment` | 최근 배포 상태 |

### 배포 파이프라인 (deploy 도메인)

`GitHub 레포 → service.yaml/Dockerfile 검증 → 이미지 빌드 → 배포 → 공개`.

**배포 모드 (`EDU_DEPLOY_MODE`)**

| 모드 | 동작 | 용도 |
| --- | --- | --- |
| `simulate` (기본) | 검증 + K8s 매니페스트 렌더만, 실제 실행 없음 | 데모·미리보기 |
| `docker` | 호스트 Docker로 **실제 이미지 빌드 + 컨테이너 기동** (`http://<host>:31000+` 로 접속) | 로컬 실배포·실증 |
| `real` | 이미지 빌드/푸시 + `kubectl apply` (K8s) | 실 서버(클러스터) |

**승인 시 자동 배포**: `edu.deploy.auto-on-approve=true`(기본)이면 운영 관리자가 승인할 때
백그라운드로 배포가 실행되어 컨테이너가 뜨고 프로그램이 자동 공개된다. (등록→승인→기동)

**레포 주소 형식**
- `https://github.com/…` : 실제 공개 레포 (git clone, docker/real 모드에서 빌드)
- `local:///workspace/examples/<name>` : compose에 마운트된 로컬 예제 (docker 모드 시험용)
- `sample://travel-settlement` : classpath 번들 예제 (검증 전용, 빌드 컨텍스트 없음)

**로컬에서 실제 컨테이너 띄워보기**
```bash
# 저장소 루트에서 (Windows PowerShell: $env:EDU_DEPLOY_MODE="docker")
EDU_DEPLOY_MODE=docker docker compose -f deploy/docker-compose.yml up --build -d
curl -X POST localhost:8088/api/deploy -H 'Content-Type: application/json' \
  -d '{"repoUrl":"local:///workspace/examples/data-summary"}'
# 응답의 url(예: http://localhost:31003) 로 접속 → 실제 배포된 컨테이너
```
compose는 호스트 `docker.sock`과 `examples/`를 백엔드에 마운트한다. 매니페스트 템플릿은
`resources/deploy-templates/service-template.yaml`, 규격은
[../docs/MSA_SERVICE_SPEC.md](../docs/MSA_SERVICE_SPEC.md), 다운로드용 AI 지시서는
`frontend/public/guides/`.

자세한 설계는 [DESIGN.md](DESIGN.md), 작업 규칙은 [AGENT.md](AGENT.md) 참조.
