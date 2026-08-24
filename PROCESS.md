# PROCESS.md · 전체 작업 프로세스 및 진행 이력

## 프로세스 개요

이 프로젝트는 3단계로 진행한다.

1. **Phase 1 — 스캐폴드 + 프론트엔드 데모**
   저장소 문서 체계, 프론트엔드(React+Vite+TSX) 데모, 7개 화면, 데모 로그인/권한 전환.
2. **Phase 2 — 백엔드 CRUD**
   Spring Boot 3 + PostgreSQL. 프로그램 등록/조회/승인 API.
3. **Phase 3 — MSA 동적 배포**
   GitHub 레포 등록 → 코드 수집 → 컨테이너 빌드 → K8s 배포. 표준 서비스 규격 검증.

각 작업 수행 시 아래 "진행 이력"에 1줄을 추가한다. (AGENT.md 2절 규칙)

## 진행 이력 (Change Log)

- 2026-08-24 — 저장소 문서 체계(README/PROCESS/AGENT) 및 docs/(가이드·아키텍처·서비스 규격) 초안 작성.
- 2026-08-24 — 프론트엔드 스캐폴드 시작: Vite+React+TSX 설정, 디자인 시스템 CSS 이식, SVG 아이콘 세트, 데모 데이터/상태/셸, 7개 화면 포팅.
- 2026-08-24 — Phase 1 완료: 프론트엔드 데모 빌드/타입체크/구동 검증 통과.
- 2026-08-24 — Phase 2 완료: Spring Boot 3 백엔드(Java 21) + PostgreSQL, 프로그램/검토/알림/사용자/분류 REST API, JSON 시더, docker-compose. 프론트엔드 API 연동(프록시 + 타입드 클라이언트, API/목업 이중 모드). Docker 빌드·기동·엔드포인트 검증 통과.
- 2026-08-24 — Phase 3 완료: MSA 동적 배포 파이프라인(regex/enum 규격 검증 → 이미지 빌드 → K8s 매니페스트 렌더 → 적용 → 공개, simulate/real 모드), 실제 K8s 매니페스트(deploy/k8s), 표준 예제 서비스(examples/sample-service). 프론트 규격검증/배포 UI. 예제 docker build+/healthz 확인, validate/deploy 엔드포인트·매니페스트 렌더 검증 통과.
