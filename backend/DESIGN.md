# DESIGN.md · 백엔드 설계 원칙

## 계층

- `Controller` (HTTP) → `Service` (트랜잭션·규칙) → `Repository` (JPA) → `domain`(엔티티).
- 응답/요청은 `dto` 레코드로만 오간다. 엔티티 직접 노출 금지.

## 도메인 경계

- `program` — 프로그램과 그 하위(이력/파일/댓글/기능/readme/태그/기능유형/기술/제공방식).
- `review` — 승인/반려/중지/재개 처리와 이력.
- `notification` — 알림 생성/조회/읽음.
- `user` — 사용자·권한의 표시용 데이터. 인가 판단에는 쓰지 않는다(계정의 단일 소스는 auth-service).
- `catalog` — 분류 체계(정적 데이터) 제공.
- `deploy` — 소스 검증·이미지 빌드·배포. 작업 큐(`DeployJob`)와 워커로 비동기 처리.
- `security` — auth-service가 발급한 JWT 자체 검증과 RBAC(인가). 인증 자체는 담당하지 않는다.

## 인증·인가

- 인증(로그인·토큰 발급)은 auth-service의 책임. backend는 **자원 서버**로서 요청의
  JWT를 동일한 `EDU_JWT_SECRET`으로 검증하고 `role` 클레임으로만 인가한다(요청마다
  auth-service 호출 없음).
- 접근 규칙은 `security.SecurityConfig` 한 곳에 모은다: 헬스·분류는 공개, 조회는 로그인,
  프로그램 등록은 CODER 이상, 검토·권한·배포·사용자 관리는 ADMIN. CORS도 시큐리티
  필터 체인으로 단일화한다.

## 데이터 모델

- 핵심 컬럼 + 컬렉션(purposes/tech/tags/run/readme/features)은 `@ElementCollection`.
- 이력/파일은 임베더블 `@ElementCollection`, 댓글은 별도 엔티티.
- 상태(`ProgramStatus`)·공개범위(`Scope`)·권한(`Role`)·처리(`ReviewAction`)·
  알림종류(`NotiKind`)는 enum이며, JSON 직렬화는 소문자 토큰(`@JsonValue`)으로
  프론트엔드와 정합성을 맞춘다.

## 상태 머신

`draft → pending → public`, `pending → rejected`, `public → stopped → public`.
전이는 `review` 도메인의 `ReviewService`가 담당하며 처리 이력과 알림을 함께 남긴다.

## 스키마 관리

- Phase 2: `ddl-auto=update`로 빠르게 진행.
- Phase 3: Flyway 마이그레이션으로 전환하고 시드를 마이그레이션/전용 프로파일로 분리.

## 오류 처리

- `common`의 전역 핸들러가 `NotFound`/검증 오류를 표준 오류 응답(JSON)으로 변환.
