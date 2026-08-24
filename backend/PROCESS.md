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
- 2026-08-24 — Phase 3: deploy 도메인(SpecParser/SourceResolver/ServiceSpecValidator/ManifestRenderer/CommandRunner/DeploymentService), Deployment 엔티티, 배포 API(validate/deploy/status), edu.deploy.* 설정. 검증: 예제 sample:// 규격검증 통과·중복/오류 검출, simulate 배포로 K8s 매니페스트 렌더 및 running 전이 확인.
