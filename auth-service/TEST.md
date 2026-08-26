# TEST.md · auth-service

## 전략

인증은 서비스 경계를 넘는 기능이므로, 단위 테스트보다 **실제 기동 후 엔드포인트 검증**을
기본으로 한다. compose 로 `auth-db` + `auth-service` + `db` + `backend` 를 함께 띄우고
토큰이 서비스 사이에서 실제로 통용되는지 확인한다.

```bash
cd deploy && cp .env.example .env && docker compose up --build -d
```

## 체크리스트

### 회원가입
- [ ] 정상 입력 → 201, 응답 `role` 이 `user`
- [ ] 비밀번호가 8자 미만이거나 영문·숫자·특수문자 중 빠진 것이 있으면 400
- [ ] 아이디가 `^[a-z][a-z0-9_]{3,19}$` 를 벗어나면 400
- [ ] 이메일 형식 위반 400
- [ ] 아이디/이메일 중복 409
- [ ] 응답 본문 어디에도 비밀번호·해시가 없다

### 중복 확인
- [ ] 미사용 값 `available: true`
- [ ] 시드 계정 값 `available: false` (아이디·이메일 각각)
- [ ] `field` 가 `username`/`email` 이 아니면 400

### 로그인 · 토큰
- [ ] 정상 로그인 200, 응답 본문에 Access Token
- [ ] `Set-Cookie: edu_refresh=…; Path=/api/auth; HttpOnly; SameSite=Lax`
- [ ] JWT 헤더 `alg` 가 **HS256**
- [ ] 페이로드에 `sub`(아이디) · `uid` · `role`(대문자) · `name` · `dept` · `typ=access`
- [ ] 비밀번호 오류와 없는 아이디가 **같은 401 메시지** (계정 존재 여부 노출 금지)

### 세션
- [ ] `/me` — 유효 토큰 200 / 토큰 없음 401 / 위조 토큰 401
- [ ] `/refresh` — 쿠키 있으면 새 Access Token, 쿠키 없으면 401
- [ ] refresh 후 이전 Refresh Token 은 폐기(회전)되어 재사용 시 401
- [ ] 폐기된 토큰 재제출 시 해당 계정의 모든 세션이 끊긴다
- [ ] `/logout` 200 후 같은 쿠키로 refresh 하면 401

### 권한
- [ ] `USER` 계정으로 `/accounts` 403
- [ ] `ADMIN` 계정으로 `/accounts` 200
- [ ] 권한 부여(`PATCH /accounts/{username}/role`)는 `ADMIN` 만 가능

### 플랫폼 backend 연동
- [ ] `GET /api/whoami` 에 Access Token 을 실으면 200 이며 role 이 일치
- [ ] 토큰 없이 호출하면 401
- [ ] `USER`/`CODER` 가 `GET /api/review/logs` 호출 시 403, `ADMIN` 은 200
- [ ] backend 는 검증 과정에서 auth-service 를 호출하지 않는다

### 시드
- [ ] 최초 기동 시 데모 계정 7명 생성, 이름·부서·역할 유지
- [ ] 비밀번호는 BCrypt 해시(`$2a$` 로 시작), 평문 저장 없음
- [ ] 재기동해도 중복 생성되지 않는다
