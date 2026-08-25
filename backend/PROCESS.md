# PROCESS.md · 백엔드 진행 이력

## 프로세스

1. 도메인 설계 → 엔티티/리포지토리 → 서비스 → 컨트롤러/DTO.
2. Docker 멀티스테이지 빌드로 컴파일·테스트.
3. compose(postgres+backend) 기동 후 엔드포인트 검증.
4. 본 이력과 관련 문서 갱신.

## 진행 이력 (Change Log)

- 2026-08-24 — Phase 2 시작: Gradle(Kotlin DSL) + Spring Boot 3 + Java 21 프로젝트 스캐폴드, application.yml, Dockerfile, 메타 문서.
- 2026-08-24 — 도메인/영속화: program·review·notification·user·catalog 패키지, JPA 엔티티/리포지토리, 프론트 데이터 기반 JSON 시더(프로그램 16·사용자 7·알림 7·이력 4).
- 2026-08-24 — 서비스/REST API: 목록/상세/등록/댓글, 승인·반려·중지·재개, 알림, 사용자 권한, 분류, 헬스. CORS·전역 예외 처리.
- 2026-08-24 — 검증: Docker 멀티스테이지 빌드 성공, compose(postgres+backend) 기동, 엔드포인트/쓰기 경로(승인·등록·댓글) curl 확인. (reserved word `user`/`by` 컬럼명 회피 수정)
- 2026-08-25 — P1-3 가용성: 서비스 템플릿(deploy-templates/service-template.yaml 및 deploy/k8s/service-template.yaml)에 무중단 롤링(strategy maxUnavailable:0/maxSurge:1), PodDisruptionBudget(maxUnavailable:1), AZ 분산(topologySpreadConstraints ScheduleAnyway)+노드 안티어피니티(preferred, soft) 추가. 검증: kind 2-replica 앱 롤링 업데이트 중 무중단(220요청 실패 1) + PDB ALLOWED DISRUPTIONS=1 확인, 서브에이전트 리뷰 PASS. 주의: maxUnavailable:0는 ResourceQuota가 N+1 허용 필요.
- 2026-08-25 — P1-1(2/2) Kaniko 인클러스터 빌드: real 모드가 host docker build/push 대신 Kaniko Job(ManifestRenderer.renderKanikoJob + kaniko-job.yaml 템플릿, kubectl apply→wait --for=condition=complete)으로 이미지 빌드. DeployProperties.buildNamespace(edu-platform). 미신뢰 입력 repoUrl/branch 정규식 검증(인자·YAML 주입 차단). docker/simulate 모드 불변. 검증: kind에서 Kaniko Job이 test-code(Go) 레포를 docker.sock 없이 빌드→인클러스터 레지스트리 push(카탈로그 확인), 서브에이전트 리뷰 PASS(보안 지적 반영).
- 2026-08-25 — P1-1(1/2) 신뢰도별 네임스페이스 자동배치: DeploymentService.resolveNamespace(CODER/ADMIN→edu-services, USER/익명/불명→edu-services-public, fail-closed), ManifestRenderer.render에 namespace 인자화, DeployProperties.namespacePublic. 검증: 내부/외부 소유자 배포 매니페스트 namespace 분기 확인(서브에이전트 리뷰 PASS).
- 2026-08-25 — P0-2 배포 오케스트레이션 분리: 인메모리 스레드풀 제거 → DeployJob 큐 + DeployWorker(@Scheduled, FOR UPDATE SKIP LOCKED 행잠금, 재시도). /deploy·/programs/{id}/deploy는 큐 적재(202), 승인 자동배포도 큐 경유. 검증: done/재시도→failed.
- 2026-08-24 — 승인 시 자동 배포: ReviewService가 승인 시 백그라운드로 DeploymentService.deploy 호출(트랜잭션 커밋 후), 배포 성공 시 프로그램 public 전환. edu.deploy.auto-on-approve(기본 true). 검증: 등록→승인→컨테이너 자동 기동(edu-svc-facility-check, /healthz ok) 확인.
- 2026-08-24 — 실배포(docker) 모드 추가: DeploymentService.dockerDeploy(호스트 Docker로 build+run+실행확인), SourceResolver local:// 지원, Deployment.hostPort, DeployProperties(host-port-base/app-host). 백엔드 Dockerfile에 git·docker CLI·kubectl 설치, compose에 docker.sock·examples 마운트. 검증: local://data-summary → 컨테이너 실제 기동 및 API 응답 확인.
- 2026-08-24 — Phase 3: deploy 도메인(SpecParser/SourceResolver/ServiceSpecValidator/ManifestRenderer/CommandRunner/DeploymentService), Deployment 엔티티, 배포 API(validate/deploy/status), edu.deploy.* 설정. 검증: 예제 sample:// 규격검증 통과·중복/오류 검출, simulate 배포로 K8s 매니페스트 렌더 및 running 전이 확인.
