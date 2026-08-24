# AGENT.md · 백엔드 작업 규칙

루트 [../AGENT.md](../AGENT.md)를 상속한다. 아래는 백엔드 고유 규칙이다.

## 규칙

1. **기능(도메인) 단위 패키지 분리.** 한 도메인은 `domain/`, `repository/`, `dto/`,
   `*Service`, `*Controller`로 구성한다. 서로 다른 도메인의 내부 클래스를 직접
   참조하지 않고 서비스/DTO로 소통한다.
2. **컨트롤러는 얇게.** 검증·매핑은 DTO와 서비스에서 처리한다.
3. **엔티티를 그대로 응답하지 않는다.** 항상 DTO(레코드)로 변환해 반환한다.
4. **토큰 문자열 정합성.** 상태/공개범위/분류 토큰은 프론트엔드와 동일한 소문자
   문자열(`public`, `all`, `doc`, `auto`, `web` …)을 사용한다.
5. **이모지 금지** (루트 규칙과 동일).

## 작업 후 갱신할 문서

- `backend/PROCESS.md` — 변경 이력 1줄 추가 (필수).
- 설계 변경 시 `backend/DESIGN.md`.
- 엔드포인트/도메인 추가 시 `backend/TEST.md`, `backend/README.md`.

## 검증

- `gradle build` (Docker: `docker build`)로 컴파일·테스트 통과.
- compose 기동 후 주요 엔드포인트 curl 확인.
