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

- 2026-09-10 — Production 배포 게이트 실행 → **BLOCKED — Production environment unavailable**(kube context = kind-edu/127.0.0.1 뿐, 분산 스토리지·LB·DNS·운영 수신처 부재 — endpoint 실확인 기준). 배포 명령 미실행, kind 결과로 대체하지 않음. 후속으로 **인프라 담당자 전달용 요구사항 문서 신설**(docs/operations/PRODUCTION_INFRA_REQUIREMENTS.md — 클러스터/스토리지/LB/DNS·TLS/오퍼레이터/오브젝트 스토리지/레지스트리/Secret 목록/수신처 검증 절차/RBAC/체크리스트/재개 게이트, 전부 repo 사실 기준). 배포 후보 `git-2737bdc` digest 재확인 일치.
- 2026-09-10 — **readiness 3차(최종 GO 게이트) — CONDITIONAL GO 유지, 잔여는 환경 부재 2건뿐**(PRODUCTION_READINESS.md §12). 실서버·운영 수신처 접근 전수 탐색 후 부재 확정(BLOCKER — 임의 값 미생성). kind 에서 리허설 런북 전 단계 실측 완주: git-5ac5c0e 배포·smoke 10/10·백업 오브젝트 실확인·PITR 41s·failover 9s(RPO 0)·워커 kill 회수·NetPol 7/7·rollback 왕복·E2E 잔존 0·HPA 2→9→2·부하 사다리(서버 5xx 0). 실측 발견·수정 2건: Redis 타임아웃(장애 시 로그인 10s→0.4s), auth 용량 미달(High 재분류)→auth HPA(2–6)+PDB 신설.
- 2026-09-10 — **readiness 2차(Condition Closure) — CONDITIONAL GO 유지, 잔여 조건 2건으로 축소**(PRODUCTION_READINESS.md §11). 해소: 수신 경로 구성+전달 체인 실검증(운영 수신처 값만 UNVERIFIED), NAT rate-limit 수정+전/후 부하 실측(10/s 46% 차단→100% 통과·방어 유지), git-85dd3d2 이미지 pull/digest 검증, staging 테넌트 배포 E2E 실측(삭제 시 hpa/pdb 잔존 결함 발견·수정·재검증). 실서버 리허설은 환경 부재로 UNVERIFIED — 런북 완성. 잔여(사람 실행): 운영 수신처 Secret 반입+확인, 실서버 리허설 1회 완주.
- 2026-09-10 — **Production readiness review 수행 — 판정 CONDITIONAL GO**(docs/operations/PRODUCTION_READINESS.md). staging(kind 멀티노드) 실검증: 테스트/빌드/kubeconform 전부 통과, smoke 4/4, CNPG failover 드릴(183s 승격), **PITR 복구 드릴 성공**(시점 정확성 포함), stale replica 재클론, 경보 파이프라인 복구·실검증. 수정 3건: auth 로그인 트랜잭션 분리(bcrypt 커넥션 점유 제거), prometheus-rules 라벨 결함+필수 alert 10종, bootstrap 관측성 플래그(Alertmanager on·CNPG PodMonitor 수집). 배포 전 조건: 실서버 리허설·Alertmanager 수신처·NAT rate-limit 튜닝·수정 커밋 이미지 확정.
- 2026-09-10 — 문서 정비: 확장성 개조(7단계) 이력·태깅 기준선(v0.8.0)·백로그 현황을 VERSIONS.md 에 반영, SCALABILITY_REVIEW.md 에 조치 완료 현황 표기.
- 2026-09-09 — staging 실검증: kind 멀티노드(cp1+worker3, Calico)에 전체 스택 실배포 검증, 실행을 막던 배선 결함 4건 수정(ServiceMonitor release 라벨 kps→monitoring, auth Service 라벨/포트명 누락, bootstrap 코어 autoscale.yaml 누락, k6 hosts 오버라이드 부재). HPA 2→10 스케일아웃·KEDA 워커 1→4 스케일아웃 실측.
- 2026-09-09 — 확장성 개조 13단계 완료(SCALABILITY_REVIEW §5 권장 조치 전체): ①코드 — 배포 트랜잭션 경계 분리, 카탈로그 페이지네이션+N+1 제거+DB 인덱스, 로그인 rate limit+데모 로그인 fail-safe, refresh_tokens 정리 스케줄러+임시파일 정리, Hikari/Tomcat 명시 설정+Flyway+graceful shutdown. ②인프라 — 멀티노드 운영 토폴로지 전환, auth-db CNPG 승격+barman 백업/PITR+PgBouncer Pooler, Redis(캐시·분산 rate-limit), Sealed Secrets+불변 이미지 태그(:latest 제거)+CI release. ③규모 검증 — k6 부하 테스트 체계(20만 계정 시드), 실측 기반 풀 튜닝(auth p95 12s→62ms), read replica 라우팅+정적 자산 캐시/압축, API/배포워커 스케일 축 분리+큐 깊이 기반 KEDA 오토스케일. 상세는 deploy/·backend/·auth-service/·frontend/PROCESS.md 참고.
- 2026-09-04 — v0.7.0 릴리스(Gitea 1~6단계 main 병합·태깅). 시각 문서 HTML 전량(html/*.html·deploy/infra-overview.html)을 원격 추적에서 제외(로컬 보관) — README·DEPLOY 참조 정리.
- 2026-09-03 — 대외 검토용 시각 문서 4종을 원격 추적에서 제외(.gitignore 등재, 로컬 보관) — README 문서 지도 링크 정리.
- 2026-09-03 — 프로그램 삭제 기능: 소유자 본인 삭제 + 관리자 삭제(DELETE /api/programs/{id}, 배포 흔적·의견·알림 동반 정리), 내 프로그램·운영 관리자 화면에 삭제 버튼. 백엔드 컴파일·프론트 빌드·목업 E2E 검증.
- 2026-09-03 — 시각 문서 4종 추가(html/: 전체 기능 정리·발표 슬라이드·ISMP 대응 설명서·대규모 인프라 산정서) 및 README 문서 지도에 링크 등록.
- 2026-08-24 — 저장소 문서 체계(README/PROCESS/AGENT) 및 docs/(가이드·아키텍처·서비스 규격) 초안 작성.
- 2026-08-24 — 프론트엔드 스캐폴드 시작: Vite+React+TSX 설정, 디자인 시스템 CSS 이식, SVG 아이콘 세트, 데모 데이터/상태/셸, 7개 화면 포팅.
- 2026-08-24 — Phase 1 완료: 프론트엔드 데모 빌드/타입체크/구동 검증 통과.
- 2026-08-24 — Phase 2 완료: Spring Boot 3 백엔드(Java 21) + PostgreSQL, 프로그램/검토/알림/사용자/분류 REST API, JSON 시더, docker-compose. 프론트엔드 API 연동(프록시 + 타입드 클라이언트, API/목업 이중 모드). Docker 빌드·기동·엔드포인트 검증 통과.
- 2026-08-25 — 멀티테넌트 보안 하드닝(내부 직원 반신뢰 + 불특정 다수 비신뢰): 신뢰 등급별 네임스페이스(PodSecurity baseline/restricted), ResourceQuota/LimitRange, NetworkPolicy(deny-by-default), gVisor RuntimeClass, Kaniko 빌드 템플릿(deploy/k8s/hardening), 서비스 템플릿 securityContext 강화. kind에서 restricted 루트 파드 거부로 검증. 루트 SECURITY.md 추가, deploy/PROCESS·AGENT 추가.
- 2026-08-25 — K8s 리허설: 로컬 kind 클러스터 생성→namespaces 적용→Go 서비스 이미지 kind load→Deployment/Service 적용. 테스트 서비스 Pod 1/1 Running, port-forward로 /healthz·API 응답 확인. deploy/k8s/README를 K8s 배포 방법(로컬 kind 리허설·플랫폼 배포·real 모드·인클러스터 빌더 주의)으로 리뉴얼. 재배포 slug 중복 버그 수정 + docker 모드 컨테이너 생존확인 추가.
- 2026-08-25 — MSA 전체 연동 실증: 다른 언어(Go) 서브 서비스(근무일수 계산기)를 github.com/05solar/test-code 에 올리고, 그 링크를 플랫폼에 등록→승인→자동 배포. git clone→Go 이미지 빌드→컨테이너 기동(edu-svc-test, 31005)→/healthz·API·HTML 동작·목록 노출까지 확인. 상세 화면에서 실제 배포 URL로 여는 버튼 연결.
- 2026-08-24 — 승인 시 자동 배포(edu.deploy.auto-on-approve) 추가: 관리자 승인 → 백그라운드 clone/build/run → 컨테이너 기동 + 프로그램 public 자동 전환(실증 완료). AI 빌드 지시서(AI_BUILD_SPEC.md)를 스택별 완성 템플릿 포함 단일 완결 문서로 총정리.
- 2026-08-24 — 실배포 강화: 배포 모드 docker 추가(호스트 Docker로 실제 이미지 빌드+컨테이너 기동), SourceResolver local:// 지원, 백엔드 이미지에 git/docker CLI/kubectl 설치, compose에 docker.sock·examples 마운트. 검증: local://examples/data-summarizer 배포 → 컨테이너 실제 기동, /healthz·API 응답 확인.
- 2026-08-24 — 등록 가이드에서 다운로드 가능한 AI 빌드 지시서 MD(AI_BUILD_SPEC + 파이썬/Node/정적 템플릿) 제공(frontend/public/guides/), 가이드 모달에 다운로드 버튼 추가.
- 2026-08-24 — 업무 분야별 기본 서비스(개인용 단발 도구) 7개 추가(doc-proofreader/seat-maker/timetable-checker/travel-allowance/asset-label/data-summarizer/doc-ocr). 표준 규격 준수, Docker 구동·계산 결과 검증 완료.
- 2026-08-24 — Phase 3 완료: MSA 동적 배포 파이프라인(regex/enum 규격 검증 → 이미지 빌드 → K8s 매니페스트 렌더 → 적용 → 공개, simulate/real 모드), 실제 K8s 매니페스트(deploy/k8s), 표준 예제 서비스(examples/sample-service). 프론트 규격검증/배포 UI. 예제 docker build+/healthz 확인, validate/deploy 엔드포인트·매니페스트 렌더 검증 통과.
- 2026-08-25 — 인증 도입: 인증 전용 마이크로서비스 auth-service(+auth-db) 신설. 회원가입/로그인/refresh/logout/me/중복확인 API, BCrypt 해시, HS256 JWT(Access 본문 + Refresh HttpOnly 쿠키·회전), USER/CODER/ADMIN 역할 모델. 플랫폼 backend에 JWT 자체 검증 필터와 Role 기반 인가 추가(서비스 간 동기 호출 없음). 프론트 인증 화면 4종(로그인/회원가입/아이디 찾기/비밀번호 찾기)과 API 연동, 데모 로그인 흐름 유지. 라우팅 /api/auth → auth-service(Vite 프록시·nginx·Ingress), docker-compose 및 K8s 매니페스트(Secret 주입) 추가, 데모 계정 7명 auth-db 이관.
- 2026-08-26 — 문서 리뉴얼: 루트 메타 문서(README/ROADMAP/SECURITY/PROCESS/AGENT)를 현재 상태 기준으로 정비. 인증·인가 계층(auth-service·JWT 자체 검증·RBAC·HttpOnly refresh·회원가입 최소권한+승인) 반영, 기본 서비스 7개(개인용 단발 도구)·서브도메인 접속·VITE_USE_API 기본 API 모드로 정정, 폐기된 옛 이름 제거.
- 2026-09-01 — 실서버(real 모드) 배포 경로 E2E 검증 + 블로커 수정: RBAC 확장(Kaniko Job·HPA·PDB·edu-services-public), EDU_DEPLOY_REGISTRY 배선, Kaniko --insecure 옵션, 테넌트/플랫폼 ingress ssl-redirect 해제(공용 호스트 308 차단 해소), edu-services-public 네임스페이스 코어 편입. kind 인클러스터 백엔드로 GitHub 레포(test-code) 배포 전 구간(clone→Kaniko 빌드/푸시→apply→pod Running→ingress 200) 검증. production Makefile 타겟(prod-preflight/registry-secret 포함) 추가.
