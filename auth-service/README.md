# auth-service · 인증 마이크로서비스

플랫폼의 **계정 정보 단일 소스**. 회원가입·로그인·토큰 발급을 담당하며, 자체 DB(`auth-db`)를
사용해 플랫폼 backend 의 PostgreSQL 과 분리되어 있다.

- Spring Boot 3.3 / Java 21 / Gradle Kotlin DSL
- PostgreSQL (`eduauth`)
- BCrypt 비밀번호 해시 · HS256 JWT

---

## 다른 서비스와의 관계

로그인 성공 시 auth-service 가 JWT 를 발급하고, 각 서비스는 **동일한 `EDU_JWT_SECRET` 으로
토큰을 직접 검증**한다. 인증이 필요한 요청마다 auth-service 를 호출하지 않는다.

```
로그인      Frontend → auth-service → Access Token + Refresh Token
플랫폼 API  Frontend → backend (Authorization: Bearer …) → backend 가 JWT 자체 검증
```

---

## 엔드포인트

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| POST | `/api/auth/signup` | 공개 | 회원가입 (항상 `role=USER`. 선택: `requestRole`=coder\|admin, `requestReason` 로 상향 권한 신청) |
| POST | `/api/auth/login` | 공개 | 로그인 · 토큰 발급 |
| POST | `/api/auth/demo-login` | 공개 | 시연용 — 역할별 데모 계정 토큰 발급 |
| POST | `/api/auth/refresh` | 공개(쿠키) | Access Token 재발급 |
| POST | `/api/auth/logout` | 공개(쿠키) | Refresh Token 폐기 |
| GET | `/api/auth/me` | 로그인 | 현재 계정 |
| GET | `/api/auth/check-duplicate` | 공개 | `?field=username\|email&value=…` |
| GET | `/api/auth/accounts` | ADMIN | 계정 목록 |
| PATCH | `/api/auth/accounts/{username}/role` | ADMIN | 권한 부여(직접) |
| GET | `/api/auth/role-requests` | ADMIN | 상향 권한 승인 대기 목록 |
| POST | `/api/auth/accounts/{username}/role-request/approve` | ADMIN | 신청 승인(신청 권한으로 상향) |
| POST | `/api/auth/accounts/{username}/role-request/reject` | ADMIN | 신청 반려(USER 유지) |
| GET | `/api/auth/health` | 공개 | 헬스 체크 |

### 권한 신청·승인 흐름
회원가입은 **항상 USER** 로 계정을 만든다. 가입자가 상향 권한이 필요하면 `requestRole`(coder|admin)을
함께 보내 "승인 대기" 상태로 신청하고(계정은 여전히 USER), 운영 관리자가 `/role-requests` 에서
확인해 승인(신청 권한으로 상향)하거나 반려(USER 유지)한다. 자가 가입으로는 권한이 상승하지 않는다.

Access Token 은 응답 본문으로, **Refresh Token 은 `HttpOnly` 쿠키(`edu_refresh`)로만** 오간다.
쿠키 경로는 `/api/auth` 로 제한되며 갱신할 때마다 회전(기존 토큰 폐기 + 재발급)한다.

## 인증 경로

이 서비스는 **인증 방식과 무관하게 JWT 를 발급하는 관문**으로 설계했다.
현재 구현된 것은 자체 ID/PW(LOCAL)뿐이며, 교육청 SSO 는 추후 이 서비스 안에 추가한다.
자원 서버가 보는 토큰의 형태는 인증 경로와 무관하게 같으므로 backend 는 변경되지 않는다.

| 경로 | 상태 | 대상 |
| --- | --- | --- |
| `LOCAL` — 회원가입 + ID/PW | 구현 완료 | 외부 사용자 |
| `DEMO` — 데모 로그인 (비밀번호 없음) | 구현 완료 | 시연 |
| `SSO` — 교육청 단일 인증 | 이번 범위 아님 | 내부 직원 · 운영 관리자 |

데모 로그인은 비밀번호 없이 역할별 데모 계정의 토큰을 발급한다. 공통 임시 비밀번호를
프론트엔드에 심지 않기 위한 것이며, **발급되는 토큰은 일반 로그인과 완전히 같다.**
따라서 데모로 진입해도 플랫폼 API 를 그대로 사용할 수 있다.

| 역할 | 데모 계정 | 환경변수 |
| --- | --- | --- |
| `USER` | `yunhaneul` (윤하늘) | `EDU_DEMO_USER` |
| `CODER` | `kimdohyun` (김도현) | `EDU_DEMO_CODER` |
| `ADMIN` | `jungwooseong` (정우성) | `EDU_DEMO_ADMIN` |

시연이 필요 없는 환경에서는 `EDU_DEMO_LOGIN=false` 로 엔드포인트를 막는다.
데모 세션은 일반 로그인(14일)보다 짧은 1일로 발급된다. 갱신할 때도 처음 발급된
유효 기간을 유지하므로 시연용 세션이 무한정 연장되지 않는다.

SSO 추가 시 필요한 변경(컬럼·매핑·역할 부여 규칙)은 루트
[README.md](../README.md) 의 "교육청 SSO 연동" 절에 정리했다.

## 역할

| 역할 | 대상 | 부여 방법 |
|---|---|---|
| `USER` | 외부 사용자 | 회원가입 기본값 |
| `CODER` | 내부 직원 | 운영 관리자가 부여 |
| `ADMIN` | 운영 관리자 | 운영 관리자가 부여 |

JWT 의 `role` 클레임은 대문자(`USER`/`CODER`/`ADMIN`), API 응답 JSON 은 기존 플랫폼 계약에 맞춘
소문자(`user`/`coder`/`admin`)를 사용한다.

---

## 환경변수

| 이름 | 기본값 | 설명 |
|---|---|---|
| `AUTH_DB_URL` | `jdbc:postgresql://localhost:5433/eduauth` | auth-db 접속 |
| `AUTH_DB_USER` / `AUTH_DB_PASSWORD` | `eduauth` | auth-db 자격 증명 |
| `PORT` | `8080` | listen 포트 |
| `CORS_ORIGINS` | `http://localhost:5173` | 허용 Origin |
| **`EDU_JWT_SECRET`** | 없음(필수) | HS256 서명 키. 32바이트 이상. backend 와 동일해야 한다 |
| `EDU_JWT_ISSUER` | `edu-auth-service` | 토큰 발급자 |
| `EDU_JWT_ACCESS_TTL` | `1800` | Access Token 유효 기간(초) |
| `EDU_JWT_REFRESH_TTL` | `1209600` | Refresh Token 유효 기간(초) · 14일 |
| `EDU_JWT_DEMO_REFRESH_TTL` | `86400` | 데모 로그인 세션 유효 기간(초) · 1일 |
| `EDU_COOKIE_SECURE` | `false` | HTTPS 배포에서는 `true` |
| `EDU_COOKIE_SAMESITE` | `Lax` | Refresh 쿠키 SameSite |
| `EDU_SEED` | `true` | 데모 계정 시드 여부 |
| `EDU_SEED_PASSWORD` | `Edu@2026!` | 데모 계정 공통 임시 비밀번호 |
| `EDU_DEMO_LOGIN` | `true` | 데모 로그인 허용 여부 |
| `EDU_DEMO_USER` / `EDU_DEMO_CODER` / `EDU_DEMO_ADMIN` | 시드 계정 | 역할별 데모 계정 아이디 |

시크릿은 소스에 두지 않는다. 로컬은 `deploy/.env`, 배포는 Kubernetes Secret 으로 주입한다.

---

## 데모 계정

기동 시 플랫폼 backend 의 `USERS_SEED` 7명이 `seed/accounts.json` 을 기준으로 이관된다.
이름·부서·역할은 유지하고 자격 증명만 새로 만든다. 이미 같은 아이디가 있으면 건너뛰므로
재기동해도 안전하다.

| 아이디 | 이름 | 부서 | 역할 |
|---|---|---|---|
| `kimdohyun` | 김도현 | 행정지원과 | CODER |
| `parkseoyeon` | 박서연 | 기획예산과 | CODER |
| `leejunho` | 이준호 | 감사관실 | CODER |
| `choimina` | 최민아 | 교육과정과 | CODER |
| `jungwooseong` | 정우성 | 정보화담당관 | ADMIN |
| `yunhaneul` | 윤하늘 | 교육과정과 | USER |
| `ohsehun` | 오세훈 | 학교지원과 | USER |

이메일은 `{아이디}@edu.local`, 비밀번호는 `EDU_SEED_PASSWORD` 를 BCrypt 로 해시해 저장하고
`mustChangePassword=true` 로 표시한다. (최초 로그인 시 변경 강제 화면은 추후 작업)

---

## 실행

```bash
# 전체 스택 (권장)
cd deploy && cp .env.example .env && docker compose up --build

# 단독 실행
EDU_JWT_SECRET=... gradle bootRun
```

## 패키지 구조

```
com.edu.auth
├── account/      회원가입 · 중복 확인 · 계정 조회 · 권한 부여
├── session/      로그인 · 토큰 갱신 · 로그아웃 · Refresh Token 기록
├── token/        JWT 발급/검증 · Refresh 쿠키
├── security/     시큐리티 필터 체인 · JWT 인증 필터
├── common/       공통 예외 · 오류 응답 · 헬스 체크
└── bootstrap/    데모 계정 시드
```
