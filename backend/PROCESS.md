# PROCESS.md · 백엔드 진행 이력

## 프로세스

1. 도메인 설계 → 엔티티/리포지토리 → 서비스 → 컨트롤러/DTO.
2. Docker 멀티스테이지 빌드로 컴파일·테스트.
3. compose(db + auth-db + auth-service + traefik + backend) 기동 후, 로그인 토큰으로
   RBAC 등급별 엔드포인트 검증.
4. 본 이력과 관련 문서 갱신.

## 진행 이력 (Change Log)

- 2026-09-09 — 조회 확장성(카탈로그 페이지네이션·DB 필터·N+1 제거): GET /api/programs·/all·/pending 을 PageResponse(items/page/size/totalElements/totalPages) 페이지 응답으로 전환(page/size 파라미터, size≤100). 필터·검색·정렬을 ProgramSpecs(Specification, EXISTS 상관 서브쿼리 — 카테시안 곱 없음)로 전부 DB 수행, 정렬은 화이트리스트(updatedAt/views/downloads + id). GET /api/programs/counts 신설(분야별 GROUP BY 집계). hibernate default_batch_fetch_size=100 으로 목록 N+1 제거(페이지당 본문1+count1+컬렉션4 = 6쿼리 고정, 테스트로 계측). 알림 unreadCount 는 DB COUNT, markAllRead 는 벌크 UPDATE. 인덱스 추가: programs(status,updated_at / status,views / status,downloads / status,cat / owner), program_tags·tech·purposes(program_id,값), notifications(to_user,is_read), deployments·comments(program_id). 프론트: List 페이지 서버 주도 검색·페이지네이션(디바운스 300ms, 20건/페이지), AppContext 는 최근 100건 작업셋. 검증: ProgramQueryTest 4건(의미 유지·페이지·쿼리수 고정·COUNT) + gradle build, 프론트 tsc+vite build 통과.
- 2026-09-09 — 배포 트랜잭션 경계 분리(확장성): DeploymentService.deploy()의 메서드 전체 @Transactional 제거 — git clone·Kaniko 빌드 대기·kubectl apply 등 장시간 외부 작업(최대 640s)이 DB 커넥션을 트랜잭션으로 점유하던 문제 해결. 상태 기록(생성·VALIDATING/BUILDING/DEPLOYING 체크포인트·FAILED)은 TransactionTemplate 짧은 트랜잭션으로 즉시 커밋하고, 성공 마무리(RUNNING 저장+프로그램 공개+알림)는 기존처럼 한 트랜잭션 유지. DeployJob 큐·SKIP LOCKED·재시도 의미 불변, API 응답 불변(중간 상태가 폴링에 실시간 노출되는 개선 효과). 검증: DeploymentServiceTransactionTest 3건(외부 작업 구간 무트랜잭션+체크포인트 선커밋/실패 시 FAILED 커밋/성공 시 프로그램 공개 동시 커밋) + gradle build 통과.
- 2026-09-03 — Gitea 6단계 통합 검증 중 수정 2건: ManifestRenderer.gitEnvBlock — fetch 주소가 http면 Kaniko에 GIT_PULL_METHOD=http env 자동 주입(kaniko는 git 컨텍스트 기본 https), 서비스 템플릿 ingress rewrite를 정규식 캡처(/svc/slug(/|$)(.*) → /$2)로 수정해 하위 경로 소실 버그 해결(deploy/k8s 사본 동기화). 검증: 비공개 Gitea 레포 real 모드 Kaniko 빌드→배포→ingress 하위 경로 200, 삭제 API real 모드 리소스 정리.
- 2026-09-03 — Gitea 4단계(webhook 자동 재배포): deploy/webhook/GiteaWebhookController 신설 — X-Gitea-Signature HMAC-SHA256 상수 시간 검증(401), 시크릿 미설정 시 404, main 브랜치·PUBLIC 프로그램 매칭(주소 정규화) 후 서버 저장 레포 주소로만 배포 큐 적재. DeployProperties gitea-webhook-secret, SecurityConfig permitAll(서명이 인증 대체 주석). 검증: kind E2E push→재배포(v5 서빙), 음성 4종 통과.
- 2026-09-03 — Gitea 3단계(파이프라인 연동): DeployProperties에 gitea-host/user/token/clone-base 추가, SourceResolver가 내부 Gitea 레포 clone 시 토큰을 git extraHeader 환경변수로 주입(인자·로그 비노출)하고 clone-base로 주소 재작성(split-horizon, *.localhost 루프백 강제 해석 대응), CommandRunner env 오버로드, Kaniko Job 템플릿에 Secret 참조 자격 env 조건 주입(템플릿 주석 placeholder 치환 버그 수정). 검증: compose E2E(비공개 레포 validate 토큰 유/무), Kaniko 렌더 YAML 파싱 2종, compileJava.
- 2026-09-03 — 프로그램 삭제 API: DELETE /api/programs/{id} 신설(소유자 본인 또는 ADMIN, SecurityConfig에 CODER+ 규칙 추가). ProgramService.requireDeletable(소유자 검증)+delete(의견·알림·프로그램), DeploymentService.removeFor(모드별 컨테이너/K8s 리소스·Traefik 라우트 정리 + 배포·큐 행 삭제), 리포지토리 4종 deleteByProgramId 추가. 검증: compileJava 통과(test 태스크는 로컬 Gradle 워커 기동 불가 — CI에서 수행).

- 2026-08-24 — Phase 2 시작: Gradle(Kotlin DSL) + Spring Boot 3 + Java 21 프로젝트 스캐폴드, application.yml, Dockerfile, 메타 문서.
- 2026-08-24 — 도메인/영속화: program·review·notification·user·catalog 패키지, JPA 엔티티/리포지토리, 프론트 데이터 기반 JSON 시더(프로그램 16·사용자 7·알림 7·이력 4).
- 2026-08-24 — 서비스/REST API: 목록/상세/등록/댓글, 승인·반려·중지·재개, 알림, 사용자 권한, 분류, 헬스. CORS·전역 예외 처리.
- 2026-08-24 — 검증: Docker 멀티스테이지 빌드 성공, compose(postgres+backend) 기동, 엔드포인트/쓰기 경로(승인·등록·댓글) curl 확인. (reserved word `user`/`by` 컬럼명 회피 수정)
- 2026-08-25 — P2-2 관측성: micrometer-registry-prometheus 추가, management로 /actuator/prometheus 노출(공통 태그 application=edu-msa-backend). backend.yaml Service 포트명 http + 라벨, ServiceMonitor(monitoring/backend-servicemonitor.yaml, release: kps). 검증: compose 백엔드 /actuator/prometheus 200(JVM/Hikari), kind에서 ServiceMonitor 디스커버리→Prometheus up=1. 서브에이전트 PASS.
- 2026-08-25 — P1-3 가용성: 서비스 템플릿(deploy-templates/service-template.yaml 및 deploy/k8s/service-template.yaml)에 무중단 롤링(strategy maxUnavailable:0/maxSurge:1), PodDisruptionBudget(maxUnavailable:1), AZ 분산(topologySpreadConstraints ScheduleAnyway)+노드 안티어피니티(preferred, soft) 추가. 검증: kind 2-replica 앱 롤링 업데이트 중 무중단(220요청 실패 1) + PDB ALLOWED DISRUPTIONS=1 확인, 서브에이전트 리뷰 PASS. 주의: maxUnavailable:0는 ResourceQuota가 N+1 허용 필요.
- 2026-08-25 — P1-1(2/2) Kaniko 인클러스터 빌드: real 모드가 host docker build/push 대신 Kaniko Job(ManifestRenderer.renderKanikoJob + kaniko-job.yaml 템플릿, kubectl apply→wait --for=condition=complete)으로 이미지 빌드. DeployProperties.buildNamespace(edu-platform). 미신뢰 입력 repoUrl/branch 정규식 검증(인자·YAML 주입 차단). docker/simulate 모드 불변. 검증: kind에서 Kaniko Job이 test-code(Go) 레포를 docker.sock 없이 빌드→인클러스터 레지스트리 push(카탈로그 확인), 서브에이전트 리뷰 PASS(보안 지적 반영).
- 2026-08-25 — P1-1(1/2) 신뢰도별 네임스페이스 자동배치: DeploymentService.resolveNamespace(CODER/ADMIN→edu-services, USER/익명/불명→edu-services-public, fail-closed), ManifestRenderer.render에 namespace 인자화, DeployProperties.namespacePublic. 검증: 내부/외부 소유자 배포 매니페스트 namespace 분기 확인(서브에이전트 리뷰 PASS).
- 2026-08-25 — P0-2 배포 오케스트레이션 분리: 인메모리 스레드풀 제거 → DeployJob 큐 + DeployWorker(@Scheduled, FOR UPDATE SKIP LOCKED 행잠금, 재시도). /deploy·/programs/{id}/deploy는 큐 적재(202), 승인 자동배포도 큐 경유. 검증: done/재시도→failed.
- 2026-08-24 — 승인 시 자동 배포: ReviewService가 승인 시 백그라운드로 DeploymentService.deploy 호출(트랜잭션 커밋 후), 배포 성공 시 프로그램 public 전환. edu.deploy.auto-on-approve(기본 true). 검증: 등록→승인→컨테이너 자동 기동(edu-svc-facility-check, /healthz ok) 확인.
- 2026-08-24 — 실배포(docker) 모드 추가: DeploymentService.dockerDeploy(호스트 Docker로 build+run+실행확인), SourceResolver local:// 지원, Deployment.hostPort, DeployProperties(host-port-base/app-host). 백엔드 Dockerfile에 git·docker CLI·kubectl 설치, compose에 docker.sock·examples 마운트. 검증: local://data-summary → 컨테이너 실제 기동 및 API 응답 확인.
- 2026-08-24 — Phase 3: deploy 도메인(SpecParser/SourceResolver/ServiceSpecValidator/ManifestRenderer/CommandRunner/DeploymentService), Deployment 엔티티, 배포 API(validate/deploy/status), edu.deploy.* 설정. 검증: 예제 sample:// 규격검증 통과·중복/오류 검출, simulate 배포로 K8s 매니페스트 렌더 및 running 전이 확인.
- 2026-08-25 — 인증 연동: spring-boot-starter-security + jjwt 추가, security 패키지(JwtVerifier/JwtAuthenticationFilter/AuthPrincipal/SecurityConfig/WhoAmIController) 신설. auth-service가 발급한 JWT를 동일 EDU_JWT_SECRET으로 자체 검증하고 role 클레임으로 인가한다(검토·권한·배포 API는 ADMIN, 프로그램 등록은 CODER 이상, 분류·헬스는 공개). CORS는 WebConfig에서 시큐리티 필터 체인으로 단일화.
