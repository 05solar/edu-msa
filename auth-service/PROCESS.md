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

## 진행 이력 (Change Log)

- 2026-08-25 — 서비스 신설: Spring Boot 3.3 / Java 21 / Gradle Kotlin DSL 스캐폴드, Dockerfile, application.yml.
- 2026-08-25 — 도메인: Account(계정·역할·임시 비밀번호 플래그) / RefreshToken(해시 저장·회전) 엔티티와 리포지토리.
- 2026-08-25 — API: signup / login / refresh / logout / me / check-duplicate, 운영 관리자용 계정 목록·권한 부여. Bean Validation 규칙을 프론트 검증과 일치시킴.
- 2026-08-25 — 보안: BCrypt 인코더, HS256 JWT 발급·검증(JwtTokenProvider), Refresh HttpOnly 쿠키(RefreshCookies), 시큐리티 필터 체인과 Role 기반 인가.
- 2026-08-25 — 데모 계정 이관: 플랫폼 USERS_SEED 7명을 seed/accounts.json 기준으로 auth-db 에 시드(이름·부서·역할 유지, 임시 비밀번호 BCrypt 해시, mustChangePassword=true). 재기동 시 중복 생성하지 않음.
