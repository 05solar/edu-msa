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

`GitHub 레포 → service.yaml/Dockerfile 검증 → 이미지 빌드 → K8s 매니페스트 렌더 →
적용 → 공개`. `edu.deploy.mode=simulate`(기본, 매니페스트만 렌더) / `real`(docker·kubectl 실행).
오프라인 시험: `repoUrl=sample://travel-settlement`. 매니페스트 템플릿은
`resources/deploy-templates/service-template.yaml`, 규격은
[../docs/MSA_SERVICE_SPEC.md](../docs/MSA_SERVICE_SPEC.md).

자세한 설계는 [DESIGN.md](DESIGN.md), 작업 규칙은 [AGENT.md](AGENT.md) 참조.
