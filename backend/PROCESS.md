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
- 2026-08-24 — 승인 시 자동 배포: ReviewService가 승인 시 백그라운드로 DeploymentService.deploy 호출(트랜잭션 커밋 후), 배포 성공 시 프로그램 public 전환. edu.deploy.auto-on-approve(기본 true). 검증: 등록→승인→컨테이너 자동 기동(edu-svc-facility-check, /healthz ok) 확인.
- 2026-08-24 — 실배포(docker) 모드 추가: DeploymentService.dockerDeploy(호스트 Docker로 build+run+실행확인), SourceResolver local:// 지원, Deployment.hostPort, DeployProperties(host-port-base/app-host). 백엔드 Dockerfile에 git·docker CLI·kubectl 설치, compose에 docker.sock·examples 마운트. 검증: local://data-summary → 컨테이너 실제 기동 및 API 응답 확인.
- 2026-08-24 — Phase 3: deploy 도메인(SpecParser/SourceResolver/ServiceSpecValidator/ManifestRenderer/CommandRunner/DeploymentService), Deployment 엔티티, 배포 API(validate/deploy/status), edu.deploy.* 설정. 검증: 예제 sample:// 규격검증 통과·중복/오류 검출, simulate 배포로 K8s 매니페스트 렌더 및 running 전이 확인.
- 2026-08-25 — 인증 연동: spring-boot-starter-security + jjwt 추가, security 패키지(JwtVerifier/JwtAuthenticationFilter/AuthPrincipal/SecurityConfig/WhoAmIController) 신설. auth-service가 발급한 JWT를 동일 EDU_JWT_SECRET으로 자체 검증하고 role 클레임으로 인가한다(검토·권한·배포 API는 ADMIN, 프로그램 등록은 CODER 이상, 분류·헬스는 공개). CORS는 WebConfig에서 시큐리티 필터 체인으로 단일화.
