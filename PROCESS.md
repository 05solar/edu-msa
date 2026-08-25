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
- 2026-08-25 — 멀티테넌트 보안 하드닝(내부 직원 반신뢰 + 불특정 다수 비신뢰): 신뢰 등급별 네임스페이스(PodSecurity baseline/restricted), ResourceQuota/LimitRange, NetworkPolicy(deny-by-default), gVisor RuntimeClass, Kaniko 빌드 템플릿(deploy/k8s/hardening), 서비스 템플릿 securityContext 강화. kind에서 restricted 루트 파드 거부로 검증. 루트 SECURITY.md 추가, deploy/PROCESS·AGENT 추가.
- 2026-08-25 — K8s 리허설: 로컬 kind 클러스터 생성→namespaces 적용→Go 서비스 이미지 kind load→Deployment/Service 적용. workdays Pod 1/1 Running, port-forward로 /healthz·API 응답 확인. deploy/k8s/README를 K8s 배포 방법(로컬 kind 리허설·플랫폼 배포·real 모드·인클러스터 빌더 주의)으로 리뉴얼. 재배포 slug 중복 버그 수정 + docker 모드 컨테이너 생존확인 추가.
- 2026-08-25 — MSA 전체 연동 실증: 다른 언어(Go) 서브 서비스(근무일수 계산기)를 github.com/05solar/test-code 에 올리고, 그 링크를 플랫폼에 등록→승인→자동 배포. git clone→Go 이미지 빌드→컨테이너 기동(edu-svc-workdays, 31005)→/healthz·API·HTML 동작·목록 노출까지 확인. 상세 화면에서 실제 배포 URL로 여는 버튼 연결.
- 2026-08-24 — 승인 시 자동 배포(edu.deploy.auto-on-approve) 추가: 관리자 승인 → 백그라운드 clone/build/run → 컨테이너 기동 + 프로그램 public 자동 전환(실증 완료). AI 빌드 지시서(AI_BUILD_SPEC.md)를 스택별 완성 템플릿 포함 단일 완결 문서로 총정리.
- 2026-08-24 — 실배포 강화: 배포 모드 docker 추가(호스트 Docker로 실제 이미지 빌드+컨테이너 기동), SourceResolver local:// 지원, 백엔드 이미지에 git/docker CLI/kubectl 설치, compose에 docker.sock·examples 마운트. 검증: local://data-summary 배포 → 컨테이너 실제 기동, /healthz·API 응답 확인.
- 2026-08-24 — 등록 가이드에서 다운로드 가능한 AI 빌드 지시서 MD(AI_BUILD_SPEC + 파이썬/Node/정적 템플릿) 제공(frontend/public/guides/), 가이드 모달에 다운로드 버튼 추가.
- 2026-08-24 — 업무 분야별 실동작 예제 프로그램 7개 추가(doc-formatter/score-stats/class-hours/budget-rate/facility-check/data-summary/civil-reply). 표준 규격 준수, Docker 구동·계산 결과 검증 완료.
- 2026-08-24 — Phase 3 완료: MSA 동적 배포 파이프라인(regex/enum 규격 검증 → 이미지 빌드 → K8s 매니페스트 렌더 → 적용 → 공개, simulate/real 모드), 실제 K8s 매니페스트(deploy/k8s), 표준 예제 서비스(examples/sample-service). 프론트 규격검증/배포 UI. 예제 docker build+/healthz 확인, validate/deploy 엔드포인트·매니페스트 렌더 검증 통과.
