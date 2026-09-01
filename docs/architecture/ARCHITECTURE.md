# 아키텍처 설계 · edu-msa

## 1. 목표

교육청 구성원이 만든 프로그램을 GitHub 레포로 올리면, 플랫폼이 자동으로 MSA
서비스로 배포해 다른 직원이 웹에서 바로 사용하게 한다.

## 2. 구성 요소

```
                         ┌─────────────────────────────┐
   사용자 (브라우저) ──▶ │  프론트엔드 (React+Vite+TSX)  │
                         └──────┬───────────────┬───────┘
                    /api/auth   │               │ /api (REST/JSON)
                         ┌──────▼─────────┐     │
                         │  auth-service   │     │
                         │  (Spring Boot)  │     │  로그인=JWT 발급
                         │  BCrypt·HS256   │     │  (자체 검증용 토큰)
                         └──────┬──────────┘     │
                         ┌──────▼─────────┐┌─────▼───────────────────┐
                         │ auth-db        ││  플랫폼 API (Spring Boot 3) │
                         │ (eduauth)      ││  - JWT 자체 검증 + RBAC     │
                         │ 계정·역할신청   ││  - 프로그램 등록/조회/승인   │
                         └────────────────┘│  - 배포 오케스트레이션 트리거 │
                                           └───┬────────────────┬─────┘
                                               │                │
                                     ┌─────────▼──────┐  ┌──────▼───────────────┐
                                     │ PostgreSQL     │  │  배포 파이프라인       │
                                     │ 프로그램·배포   │  │  clone→검증→build→push │
                                     └────────────────┘  └──────┬───────────────┘
                                                                 │ docker / kubectl
                                    ┌────────────────────────────▼──────────────┐
                                    │  런타임 (docker: Traefik+eduproxy · real: K8s) │
                                    │  서비스별 컨테이너/파드 · http://<slug>.localhost │
                                    └────────────────────────────────────────────┘
```

`auth-service`(로컬 `:8089`)는 계정의 단일 소스로, 자체 DB(`auth-db`, `eduauth`)를
플랫폼 DB와 분리해 운영한다. 로그인 시 발급한 JWT를 **플랫폼 API와 각 서비스가 동일한
`EDU_JWT_SECRET`으로 자체 검증**하므로, 요청마다 auth-service를 호출하지 않는다.

## 3. 계층별 책임

### 프론트엔드 (`frontend/`)
- 프로그램 탐색/검색/상세, 등록 폼, 내 프로그램, 마이페이지(권한 신청), 운영 관리자 화면.
- 역할(일반 사용자 USER / 바이브 코더 CODER / 운영 관리자 ADMIN)에 따른 메뉴·권한 분기.
- 기본적으로 API 모드(`VITE_USE_API` 기본값 `true`)로 동작하며 로그인 후 이용한다.
  Vite 개발 프록시는 `/api/auth`→`:8089`(auth-service), `/api`→`:8088`(플랫폼 API)로 전달한다.
- Access 토큰은 응답 본문으로 받아 메모리에 보관하고, Refresh 토큰은 HttpOnly 쿠키
  (`edu_refresh`, path `/api/auth`)로 저장한다. 새로고침 시 `/api/auth/refresh`로 세션을 복구한다.

### 인증 서비스 (`auth-service/`)
- 계정의 단일 소스. 자체 DB(`auth-db`, `eduauth`)를 플랫폼 DB와 분리 운영.
- BCrypt 비밀번호 해시 + HS256 JWT 발급. 로컬 `:8089`, K8s는 `deploy/k8s/auth/`.
- 인증 경로: `LOCAL`(ID/PW), `DEMO`(비밀번호 없는 시연) 구현. 교육청 SSO는 auth-service에
  추가 예정이며, 토큰 형태가 동일하므로 플랫폼 API·서비스는 무변경으로 수용한다.
- **권한 상승은 자가 신청만으로 불가**: 회원가입은 항상 `USER`. 상향 권한(CODER/ADMIN)은
  가입 시 `requestRole` 또는 로그인 후 `POST /api/auth/role-request`로 "신청"만 보관하고,
  운영 관리자가 `GET /api/auth/role-requests`로 확인해 승인/반려한다. 신청자는 승인 전
  `DELETE /api/auth/role-request`로 취소 가능.

### 플랫폼 API (`backend/`)
- 도메인: `program`(등록/조회/버전), `review`(승인/반려), `deploy`(배포 오케스트레이션),
  `catalog`(분류 체계). 사용자·인증은 auth-service가 담당한다.
- **JWT 자체 검증 + RBAC**(`com.edu.msa.security.SecurityConfig`):
  `/api/health`·`/api/catalog/**`는 공개, `/api/programs`(로그인 사용자 → PUBLIC 목록),
  `POST /api/programs`는 CODER 이상, `/api/programs/all`·`/api/programs/*/deploy`·`/api/users`는
  ADMIN, 그 외는 로그인 필요. 검증은 auth-service와 공유하는 `EDU_JWT_SECRET`으로 로컬 수행.
- 기능별 패키지 분리. PostgreSQL 영속화.

### 배포 파이프라인 (`backend/deploy` + `deploy/`)
- GitHub clone → `service.yaml`/`Dockerfile` 정적 검증 → 이미지 빌드 → (docker) eduproxy 합류
  또는 (real) 레지스트리 push + K8s 적용 → 헬스 확인 → 상태 전환.
- docker 모드에서는 컨테이너를 `eduproxy` 네트워크에 합류시키고 Traefik `/dynamic/<slug>.yml`
  라우트를 기록한 뒤, 컨테이너 `/healthz` 응답까지 대기(readiness)해 첫 접속 502를 방지한다.
- 표준 규격은 [MSA_SERVICE_SPEC.md](MSA_SERVICE_SPEC.md).

### 서비스 라우팅 (서브도메인)
- 배포된 서비스는 포트가 아니라 **`http://<slug>.localhost`**(Traefik 리버스 프록시, 파일
  프로바이더)로 열린다. 브라우저가 `*.localhost`를 127.0.0.1로 처리 → Traefik이 Host 헤더로
  해당 컨테이너에 라우팅한다. 실서버(real)에서는 ingress-nginx가 서브도메인/경로를 처리한다.

## 4. 데이터 모델 (초안)

- `program(id, name, slug, category, summary, description, repo_url, branch,
  owner_id, status, scope, version, created_at, updated_at)`
- `program_purpose(program_id, purpose)` · `program_tech(program_id, tech)`
- `program_version(id, program_id, version, changelog, image_tag, created_at)`
- `review(id, program_id, reviewer_id, action, memo, created_at)`
- `favorite(user_id, program_id)`
- `notification(id, to_user_id, kind, title, sub, program_id, read, created_at)`

계정·역할은 플랫폼 DB가 아니라 **auth-db(`eduauth`)**에 있다: `account`(BCrypt 해시,
`role` ∈ USER/CODER/ADMIN) · `role_request`(승인 대기 신청). 플랫폼은 JWT의 subject·role로
사용자를 식별하며 별도 사용자 테이블을 두지 않는다.

`status`: draft | pending | public | rejected | stopped

## 5. 배포 상태 머신

```
draft ─(제출)→ pending ─(승인+배포성공)→ public
  pending ─(반려)→ rejected
  public ─(중지)→ stopped ─(재개)→ public
```

## 6. K8s(실서버) 아키텍처

- 신뢰등급 네임스페이스 분리: `edu-services`(baseline) / `edu-services-public`(restricted),
  fail-closed 정책.
- 안전 빌드는 Kaniko + 로컬 레지스트리, 유휴 시 KEDA scale-to-zero.
- 관측성: Prometheus / Loki / Tempo. 게이트웨이는 ingress-nginx(WAF), 인증서는 cert-manager.
- 인증 계층: `deploy/k8s/auth/`(auth-db.yaml, auth-service.yaml, `edu-auth-jwt` Secret).
- apply 순서: `namespaces → hardening → rbac → postgres → auth-db → auth-service →
  backend → frontend → ingress`.

## 7. 기술 결정 (ADR 요약)

- 프론트: React 18 + Vite + TypeScript. 상태는 Context 기반 경량 스토어(외부
  상태 라이브러리 미도입).
- 백엔드: Spring Boot 3, Java 21, Gradle Kotlin DSL.
- 인증: auth-service를 독립 서비스로 분리하고 auth-db를 플랫폼 DB와 별도로 둔다.
  JWT(HS256)를 각 서비스가 공유 시크릿으로 자체 검증(무상태 인증).
- DB: PostgreSQL(플랫폼 `edu` · 인증 `eduauth` 분리).
- 오케스트레이션: 로컬은 docker-compose + Traefik(eduproxy), 실서버는 Kubernetes.
- 아이콘: 인라인 SVG 세트(이모지 금지). 알림/토스트에 좌측 색상 바 금지(아이콘+텍스트만).
