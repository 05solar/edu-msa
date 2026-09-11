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

- 2026-09-11 — **P2-3 ad-hoc 배포 경로 제거(CLOSED)**: 실호출자 없는 POST /api/deploy(프로그램 없는 배포)를 제거하고 enqueue 에 programId 불변식 가드 추가 — 모든 배포가 프로그램 lifecycle(P0-2 동시성·P1-5 소유권/정리)을 타도록 일원화. legacy null 데이터는 이력 행뿐(active 0)이라 정리 불요. staging 검증(제거·validate 유지·E2E+redeploy·신규 invariant 0). 상세 backend/PROCESS.md.

- 2026-09-11 — **P2-2 service.yaml 리소스 검증-실제 limit 정합(CLOSED)**: 검증 상한을 고정값이 아닌 렌더 limits 와 동일한 플랫폼 설정(단일 출처)으로 전환 — request>limit 스펙이 kubectl 단계 대신 검증 단계에서 명확한 메시지로 조기 차단(빌드 자원 낭비 제거). quantity 등가 비교·env override 동시 반영·기동 시 설정 오류 fail-fast/WARN. staging 재현→차단 실측·E2E 완주. 상세 backend/PROCESS.md.

- 2026-09-11 — **P2-1 알림 페이지네이션·보존 정책(CLOSED)**: 목록 전건 로드 제거(서버 페이지 20/최대 100·created_at+id 결정적 최신순·V5), 읽은 알림 90일 보존 후 배치 삭제 스케줄러(미읽음 자동 삭제 없음), 실 PG 101k EXPLAIN(BitmapOr·0.6ms) 검증, 프론트 더보기+서버 unread 배지(역할 공지 표시 회귀 수정). uid 소유권·IDOR 차단 불변. 상세 backend/PROCESS.md.

- 2026-09-11 — **P1-5 불변 UID identity 전환(CLOSED · Remaining P1: 0)**: 소유권(programs.owner_id)·배포 신뢰(owner_trusted 스냅샷)·알림 수신(recipient_id/role)을 표시 이름에서 JWT uid 로 전환 — 동명이인 계정의 타인 프로그램 삭제(재현: 204)·알림 열람(11건) 차단, 클라이언트 owner 위조 무시, "김도현" 하드코딩 제거. Flyway V4 는 backfill 없이 안전 전환(오매핑 0, legacy 는 fail-closed+ADMIN remediation). staging 실HTTP·테넌트 E2E·Flyway 2경로 실측. 상세 backend/PROCESS.md.
- 2026-09-11 — GitHub 언어 통계 보정: .gitattributes 신설 — frontend/public/guides/*.html(다운로드용 가이드 2개, 이미지 인라인으로 약 3.9MB — 저장소 바이트의 80%+)을 linguist-documentation 으로 분류해 언어 비율이 HTML 로 표시되던 문제 해결. 파일 추적·배포는 불변, push 후 Linguist 재계산 시 반영.
- 2026-09-11 — **P1-4 CommandRunner 타임아웃 견고성(CLOSED)**: readAllBytes 의 EOF 대기가 waitFor(timeout)를 막던 구조(재현: timeout 1s 에 8~30s 블로킹·자식 잔존·출력 무제한)를 리더 스레드 분리+프로세스 트리 kill(grace 후 forcibly)+출력 상한(1MiB·truncated 표시)+stdin 차단+인터럽트 정리로 수정. 반환 계약·재시도 정책·argv 실행 방식 불변. CommandRunnerTest 11건 + gradle 전체 + staging E2E 실측. 상세 backend/PROCESS.md.
- 2026-09-11 — **P1-3 refresh token 회전 동시성(CLOSED)**: auth-service 회전을 DB 조건부 UPDATE 로 원자화(동일 토큰 동시 N요청 → 정확히 1 성공·신규 토큰 1개, replica 무관). 폐기 재제출은 revoked_at(V2) 기준 grace 창(30s)으로 "동시 경쟁 패배(정상)"와 "오래된 재사용(탈취 — 전 세션 폐기)" 구분, 만료 쿠키 제출로는 전 세션을 끊지 않게 정련. 수정 전 staging 실측 10/10 성공(불변식 붕괴) → 수정 후 1/10·active 1 실측, 20 병렬 452ms·deadlock 0. 상세 auth-service/PROCESS.md.
- 2026-09-11 — **P1-2 service.yaml 파싱·매니페스트 생성 하드닝(CLOSED)**: SnakeYAML SafeConstructor+LoaderOptions(중복 키·alias·중첩·크기 제한), 정의 외 필드 거부(화이트리스트), 렌더러 raw 치환 4필드(name/health/cpu/memory) 엄격 검증으로 YAML/매니페스트 주입 차단(개행·따옴표·중괄호·콜론 금지, cpu≤2000m·memory≤2Gi=LimitRange max), 파싱 오류는 영구 오류(재시도 금지)·사용자용 한 줄 메시지. 수정 전 staging 실HTTP 로 name 주입 검증 통과 재현 → 수정 후 4종 차단+정상 레포 무회귀+E2E 완주 실측. 규격 문서에 형식·상한 명시. 상세 backend/PROCESS.md.
- 2026-09-11 — **P1-1 알림 IDOR 차단(CLOSED)**: 알림 API 대상 사용자를 클라이언트 `?to=` 파라미터가 아니라 JWT principal 로 강제(4개 엔드포인트), 단일 read 는 id+소유자 원자 UPDATE(0행=404, 존재 비노출), 프론트는 사용자 식별자 전송 제거. staging 실HTTP 로 수정 전 타인 알림 열람·변조 재현 → 수정 후 전 경로 차단 실측. gradle 전체 + NotificationOwnershipTest 6건 + frontend 빌드 통과. 상세 backend/PROCESS.md.
- 2026-09-11 — **P0-2 배포 slug TOCTOU·중복 배포 경쟁 해소(CLOSED)**: slug 소유권 예약 테이블 slug_claims(PK 경쟁이 원자 심판·Flyway V3, 기존 데이터 충돌 시 명시적 실패 가드) + 같은 프로그램 active 배포 1건 강제(부분 유니크 uq_deploy_jobs_active_program + enqueue 멱등·409) + 영구 오류(규격 위반·slug 충돌) 재시도 금지(completeTerminal) + 테넌트 리소스 소유권 라벨(edu.msa/program-id·deployment-id)과 삭제 전 검증(불일치 스킵). 검증: gradle 전체 통과(동시성 테스트 3파일 신규), PostgreSQL 실측(동시 INSERT 경쟁·Flyway 신규/기존/가드 3경로), staging 워커 2replica 동일 slug 동시 배포 1승/1영구실패·승자 무손상·E2E 39s 회귀. 상세 backend/PROCESS.md.
- 2026-09-11 — **P0-1 Kaniko 비신뢰 빌드 격리(CLOSED)**: 빌드 전용 격리 ns `edu-build` 신설(deploy/k8s/platform/build.yaml — PSA baseline+restricted audit/warn, default-deny NetPol(DNS·공인 egress·레지스트리 5000·Gitea 만 허용, K8s API·사설망·메타데이터 차단), Quota/LimitRange, 전용 SA edu-kaniko+default SA 토큰 미마운트). Kaniko Job 템플릿 하드닝(seccomp RuntimeDefault·caps drop ALL+최소 6종·no-priv-esc·리소스 상한 cpu1/mem2Gi/eph8Gi·activeDeadline 900s), edu-builder RBAC 를 edu-platform→edu-build 로 이동(+구 Role/Binding 마이그레이션 삭제), backend/worker 매니페스트에 EDU_DEPLOY_BUILD_NAMESPACE=edu-build 명시, bootstrap 배선(build.yaml 적용·gitea 토큰 edu-build 동기화·default SA patch). 검증: gradle 전체 테스트(신규 KanikoJobIsolationTest 4건 포함) 통과, kubeconform Valid, kind(Calico) 실측 — 악성 Dockerfile 테스트 A(SA토큰)·B(K8s API DNS/IP)·C(플랫폼 DB/Redis/메타데이터/자격)·D(privileged/hostPath PSA 거부)·E(2Gi 상한에서 3GB 할당 거부) 전부 차단 확인, 정상 테넌트 E2E(등록→Kaniko 빌드 36s→push→기동→healthz 200→삭제 잔존 0) 완주. 잔여 위험: RUN 이 push 자격 판독 가능(Kaniko 구조 한계 — 레포 push 전용·단기 계정 요구, INFRA_REQUIREMENTS §11 ⑨).
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
