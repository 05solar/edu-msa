# PROCESS.md · auth-service

## 작업 절차

1. 관련 메타 문서(루트 `AGENT.md`, 본 문서, `README.md`)를 먼저 읽는다.
2. 기능(도메인) 단위 패키지로 구현한다. (`account` / `session` / `token` / `security` / `common` / `bootstrap`)
3. `gradle build` 로 컴파일·테스트, compose 기동 후 엔드포인트를 검증한다.
4. 본 이력과 관련 문서를 갱신한다.

## 설계 원칙

- **계정 정보의 단일 소스.** 사용자·비밀번호·권한은 auth-db 에만 둔다.
  플랫폼 backend 의 `app_users` 는 표시용 데이터이며 인가 판단에 쓰지 않는다.
- **평문 비밀번호를 저장하지 않는다.** BCrypt 해시만 보관하고 어떤 응답에도 포함하지 않는다.
- **토큰 자체 검증.** 각 서비스가 동일한 시크릿으로 JWT 를 직접 검증하고,
  인증이 필요한 요청마다 auth-service 를 호출하지 않는다.
- **시크릿은 소스에 두지 않는다.** 환경변수 또는 Kubernetes Secret 으로 주입한다.
- **Refresh Token 은 브라우저 스크립트가 읽을 수 없게 한다.** HttpOnly 쿠키로만 오가며
  갱신 시 회전시키고, 폐기된 토큰이 재사용되면 해당 계정의 세션을 모두 끊는다.
- **회원가입은 항상 최소 권한(USER).** CODER/ADMIN 상향은 신청(가입 시 `requestRole`
  또는 로그인 후 `POST /api/auth/role-request`)으로 접수만 하고, 운영 관리자의 승인으로만
  실제 권한을 올린다. 자가 가입·자가 신청만으로는 권한이 상승하지 않는다.

## 진행 이력 (Change Log)

- 2026-09-09 — 운영 런타임 정비: HikariCP(DB_POOL_*)·Tomcat(TOMCAT_*) 환경변수화(보수적 기본값), Flyway 도입(V1__init_auth_schema.sql — Hibernate 기대 DDL 기준, 기존 DB 는 baseline-on-migrate=1 로 V1 스킵) + ddl-auto 기본 validate, graceful shutdown(20s), Dockerfile exec ENTRYPOINT + JAVA_OPTS(MaxRAMPercentage 75 — limit 768Mi 대비 힙 ~576Mi), K8s preStop 5s + terminationGracePeriodSeconds 45. SchemaMigrationTest 로 V1 실적용→validate→시드 INSERT 검증. 검증: gradle build 10/10.
- 2026-09-09 — refresh_tokens 정리 스케줄러: RefreshTokenCleaner(@Scheduled, 기본 1시간 주기·기동 60초 지연) — "만료 + 보존기간(기본 24h) 경과" 행만 배치(기본 1만건, LIMIT 서브쿼리 네이티브 삭제)로 반복 삭제해 테이블 무한 증가를 차단. 폐기됐지만 만료 전인 행은 탈취 감지용으로 만료까지 보존. 배치별 짧은 트랜잭션(TransactionTemplate) + 멱등 삭제라 다중 replica 동시 실행에 안전. 정책값은 edu.auth.cleanup.*(EDU_CLEANUP_*)로 분리, expires_at 인덱스 추가, 실패는 로그만 남기고 다음 주기 재시도. 검증: RefreshTokenCleanupTest 2건(보존 정책·배치 반복·무삭제 경로) + gradle build.
- 2026-09-09 — 인증 방어 계층(brute force/크리덴셜 스터핑·데모 fail-safe): ratelimit 패키지 신설 — AttemptStore 추상화(추후 Redis 교체용) + InMemoryAttemptStore(고정 윈도우·상한 초과 시 만료분 청소) + RateLimitProperties(정책값 전부 환경변수화) + LoginGuard(계정 기준 5회/10분→5분 차단 · IP 합산 30회/10분→10분 차단 · 실패 지수 백오프 지연 300ms~2s · refresh IP 기준 30회/60초→5분 차단). 가드는 트랜잭션 밖(SessionController)에서 적용해 지연이 DB 커넥션을 잡지 않음. 429 + Retry-After 응답(TooManyRequestsException). 데모 로그인 기본값 true→false(fail-safe) — compose 는 명시적 true, K8s 매니페스트는 EDU_SEED/EDU_DEMO_LOGIN "false" 고정 + bootstrap.sh kind 모드만 "true" 치환, ingress 는 /api/auth 전용 Ingress 분리(limit-rps 5). 검증: AuthGuardTest 5건 + DemoLoginEnabledTest 1건 + gradle build 통과.
- 2026-08-25 — 서비스 신설: Spring Boot 3.3 / Java 21 / Gradle Kotlin DSL 스캐폴드, Dockerfile, application.yml.
- 2026-08-25 — 도메인: Account(계정·역할·임시 비밀번호 플래그) / RefreshToken(해시 저장·회전) 엔티티와 리포지토리.
- 2026-08-25 — API: signup / login / refresh / logout / me / check-duplicate, 운영 관리자용 계정 목록·권한 부여. Bean Validation 규칙을 프론트 검증과 일치시킴.
- 2026-08-25 — 보안: BCrypt 인코더, HS256 JWT 발급·검증(JwtTokenProvider), Refresh HttpOnly 쿠키(RefreshCookies), 시큐리티 필터 체인과 Role 기반 인가.
- 2026-08-25 — 데모 계정 이관: 플랫폼 USERS_SEED 7명을 seed/accounts.json 기준으로 auth-db 에 시드(이름·부서·역할 유지, 임시 비밀번호 BCrypt 해시, mustChangePassword=true). 재기동 시 중복 생성하지 않음.
- 2026-08-25 — 상향 권한 신청·승인: 회원가입은 항상 USER 로 고정하고 CODER/ADMIN 은 신청으로만 접수. 가입 시 `requestRole`/`requestReason`, 로그인 후 `POST /api/auth/role-request`(취소 `DELETE`)로 신청하며 계정 권한은 USER 유지(승인 대기만 보관). 운영 관리자용 `GET /api/auth/role-requests`, 승인 `POST .../{username}/role-request/approve`, 반려 `POST .../reject` 추가. 자가 신청만으로 권한 상승 불가. `/me` 응답에 신청 상태 포함.
