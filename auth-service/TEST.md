# TEST.md · auth-service

## 전략

인증은 서비스 경계를 넘는 기능이므로, 단위 테스트보다 **실제 기동 후 엔드포인트 검증**을
기본으로 한다. compose 로 `auth-db` + `auth-service` + `db` + `backend` 를 함께 띄우고
토큰이 서비스 사이에서 실제로 통용되는지 확인한다.

```bash
cd deploy && cp .env.example .env && docker compose up --build -d
```

## 자동 테스트

- `AuthServiceApplicationTests` — 컨텍스트 로드(H2, test 프로파일).
- `AuthGuardTest` — 로그인 남용 방어(MockMvc): 정상 로그인·성공 시 카운터 초기화 /
  반복 실패 시 계정 임시 차단(429 + Retry-After, 다른 IP 에서도 유지) /
  IP 합산 실패 차단(다른 IP 의 같은 계정은 정상) / refresh 반복 실패 IP 차단 /
  데모 로그인 기본 비활성(404).
- `DemoLoginEnabledTest` — `edu.auth.demo.enabled=true` 로 명시한 환경에서만 데모 로그인 동작.
- `RefreshRotationConcurrencyTest` — P1-3 회전 동시성: 동일 토큰 동시 2/10 요청 →
  정확히 1 성공·신규 토큰 1개(CountDownLatch), grace 창 안 재사용은 401 이되 승자
  세션 유지, 폐기 시각 백데이트(오래된 재사용 공격)는 전 세션 폐기, 만료·logout·위조
  토큰 401(500 없음·새 토큰 없음).
- `RefreshTokenCleanupTest` — 만료+보존기간 경과 행만 배치 삭제되고, 보존기간 내 만료·
  폐기(미만료)·활성 행은 남는지 검증(batch-size=2 로 배치 반복 포함).
- `SchemaMigrationTest` — 빈 H2(MariaDB 모드, `db/vendor/h2` 판)에 Flyway V1 실적용 →
  Hibernate `validate` 로 엔티티-스키마 일치 확인 → 시드 INSERT 까지 검증.
- `MariaDbSchemaMigrationIT` — 실 MariaDB 11.4 컨테이너(Testcontainers)로 2건 검증:
  `db/vendor/mariadb` 판 마이그레이션 실적용 + Hibernate `validate` 정합.
  로컬 Docker 데몬 필요(없으면 스킵). 로컬 Windows + Docker Engine 29 는
  `~/.docker-java.properties` 에 `api.version=1.44` 한 줄이 필요하다
  (docker-java 가 구식 API 1.32 로 협상하면 400 — CI Linux 는 불필요).
- `RedisAttemptStoreTest` — Redis 원자 연산 매핑(INCR+최초 EXPIRE/SET EX/TTL/DEL) 검증.
- `FailoverAttemptStoreTest` — Redis 전면 장애 시 인메모리 폴백으로 카운트·차단이
  유지되고(fail-open 금지) 폴백 횟수가 메트릭으로 집계되는지 검증.

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

### 상향 권한 신청·승인
- [ ] 가입 시 `requestRole=coder` 를 보내도 생성된 계정 `role` 은 `user` (신청만 접수)
- [ ] 로그인 후 `POST /api/auth/role-request {requestRole,requestReason}` → 200, 계정은 `user` 유지·"승인 대기"
- [ ] 신청 후 `/me` 응답에 신청 상태(요청 역할·사유)가 포함
- [ ] 본인 `DELETE /api/auth/role-request` → 승인 전 신청 취소, 대기 목록에서 사라짐
- [ ] `USER` 로 `GET /api/auth/role-requests` 403, `ADMIN` 은 200(대기 목록 노출)
- [ ] `ADMIN` `POST /accounts/{username}/role-request/approve` → 계정 `role` 이 신청 권한으로 상향, 대기 해소
- [ ] `ADMIN` `POST /accounts/{username}/role-request/reject` → 계정 `role` 은 `user` 유지, 대기 해소
- [ ] 자가 가입·자가 신청만으로는 권한이 상승하지 않는다(승인 없이는 항상 `user`)

### 플랫폼 backend 연동
- [ ] `GET /api/whoami` 에 Access Token 을 실으면 200 이며 role 이 일치
- [ ] 토큰 없이 호출하면 401
- [ ] `USER`/`CODER` 가 `GET /api/review/logs` 호출 시 403, `ADMIN` 은 200
- [ ] backend 는 검증 과정에서 auth-service 를 호출하지 않는다

### 시드
- [ ] 최초 기동 시 데모 계정 7명 생성, 이름·부서·역할 유지
- [ ] 비밀번호는 BCrypt 해시(`$2a$` 로 시작), 평문 저장 없음
- [ ] 재기동해도 중복 생성되지 않는다
