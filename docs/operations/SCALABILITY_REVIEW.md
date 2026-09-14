# SCALABILITY_REVIEW.md · 수십만 사용자 규모 적합성 검토

> 이 문서의 DB 관련 서술(PostgreSQL/CNPG/PgBouncer 등)은 작성 시점 기준이다.
> 2026-09-14 MariaDB 전환([MARIADB_PLAN.md](../planning/MARIADB_PLAN.md)) 이후 DB 스택은
> MariaDB 11.4 단일 인스턴스 + mariadb-dump 백업이며, HA·풀러·replica 는 후속 트랙이다.

> 검토일: 2026-09-09 · 대상: `deploy/`(K8s·compose 전체), `backend/`, `auth-service/`, `frontend/`(서빙 구성)
> 기준: **수십만 명(예: 20만~50만 계정, 피크 동시접속 1만~5만, 피크 수천 RPS)** 을 감당할 수 있는가.
>
> **조치 현황 (2026-09-10): §5 권장 조치 0~2단계(1~13) 전부 구현·kind 멀티노드 staging 실검증 완료.**
> 본문의 지적 사항은 검토 시점 기준 기록으로 보존한다. 조치 이력은
> [VERSIONS.md §2 7단계](../planning/VERSIONS.md) 및 `deploy/`·`backend/`·`auth-service/` 각 PROCESS.md 참고.
> 잔여 권장(auth HPA·로그인 트랜잭션 bcrypt 분리 등)은 VERSIONS.md §4 백로그로 이관.

---

## 총평

**현재 구조는 "아키텍처 방향은 올바르나, 실제 용량과 데이터 계층·운영 체계가 데모/소규모 기관 규모에 맞춰져 있어 수십만 명 서비스에는 그대로 투입할 수 없다.**

- **잘 설계된 부분(수평 확장의 전제 조건)은 갖췄다.** 무상태(stateless) 애플리케이션, JWT 자체 검증(인증 서버 비호출), DB 기반 배포 작업 큐(`SKIP LOCKED`), HPA/PDB, 멀티테넌트 격리(PSA/NetworkPolicy/Quota/gVisor), 관측성 3축(Prometheus/Loki/Tempo)까지 뼈대는 프로덕션 지향이다.
- **그러나 규모를 결정하는 실질 요소들이 미흡하다.** 단일 서버(k3s) 전제, 장난감 수준의 DB 사이징(1Gi)·백업 부재, 인증 DB 단일 인스턴스(SPOF), 커넥션 풀·캐시 부재, 페이지네이션 없는 전건 조회 + N+1, 로그인 rate limit 부재, 배포 트랜잭션의 초장시간 커넥션 점유 등은 수십만 규모에서 **정상 트래픽만으로도 장애를 일으키는 수준**이다.

| 영역 | 판정 |
|---|---|
| 애플리케이션 무상태 설계 (수평 확장 가능성) | **적합** |
| 클러스터/물리 토폴로지 | **부적합** (단일 노드 전제) |
| 데이터 계층 (DB 사이징·HA·백업·풀링) | **부적합** |
| 조회 경로 성능 (쿼리·캐시·페이지네이션) | **부적합** |
| 인증 계층 (구조는 좋음 · 방어/튜닝 미흡) | **미흡** |
| 엣지/트래픽 (Ingress·CDN·rate limit) | **미흡** |
| 배포/운영 체계 (이미지 태깅·마이그레이션·시크릿) | **미흡** |
| 멀티테넌트 격리·보안 하드닝 | **적합** |
| 관측성 (metrics/logs/traces/alerts) | **적합** (저장소 사이징만 보강) |

---

## 1. 잘 되어 있는 부분 (유지)

| 항목 | 근거 |
|---|---|
| 무상태 백엔드 — 인메모리 세션·가변 static 없음, JWT 서명만으로 자체 검증 | `backend SecurityConfig.java:46` (STATELESS), `JwtAuthenticationFilter.java:37` |
| 리프레시 토큰 DB 저장(SHA-256 해시) + 회전 + 탈취 감지 시 전체 세션 무효화 | `auth-service AuthService.java:86-122` |
| 배포 작업이 요청 스레드가 아닌 DB 큐로 분리, `SELECT … FOR UPDATE SKIP LOCKED` 로 다중 인스턴스 안전 | `DeploymentController.java:47-58` (202 반환), `DeployJobRepository.java:14` |
| 외부 호출(git/kubectl/docker) 전부 명시적 타임아웃 + 큐 기반 재시도 | `CommandRunner.java:29-33`, `DeployJobService.java:48-49` |
| HPA + PDB (플랫폼·테넌트 모두), 테넌트 무중단 롤링(maxUnavailable: 0)·topologySpread·anti-affinity | `deploy/k8s/platform/autoscale.yaml`, `deploy/k8s/service-template.yaml` |
| 멀티테넌트 격리 — 신뢰등급별 ns + PodSecurity(baseline/restricted), default-deny NetworkPolicy, ResourceQuota/LimitRange, gVisor, Kaniko(도커 소켓 미사용 빌드) | `deploy/k8s/hardening/*` |
| 관측성 — actuator/prometheus 노출, kube-prometheus-stack·Loki·Tempo·Alertmanager 구성 | `backend application.yml:19-30`, `deploy/k8s/platform/monitoring·logging·tracing/` |
| 사용자 업로드 파일의 로컬 디스크 저장 없음 (바이너리 미보관) | backend 전체 (`MultipartFile` 사용 0건) |
| CNPG 기반 DB HA 전환 경로 존재 | `deploy/k8s/platform/postgres-ha.yaml` |

---

## 2. 심각(Critical) — 이대로는 수십만 규모에서 장애 확정

### C-1. 단일 물리 서버 + k3s 단일 노드 전제
- `deploy/PRODUCTION.md` 전체가 "GPU 박스 1대 + k3s"를 실서버 시나리오로 전제한다. 노드 1대는 컨트롤플레인·워커·DB·인그레스가 전부 한 장비에 있는 구조로, **하드웨어 장애 = 전면 장애**이고 수직 확장 외에는 용량을 늘릴 수 없다.
- 수십만 사용자라면 최소: **멀티 노드(워커 3대 이상) + HA 컨트롤플레인(3대) 또는 매니지드 K8s**, 인그레스 앞 L4 로드밸런서, 노드 장애 시 재스케줄을 견딜 스토리지(로컬 `local-path` PVC는 노드 종속이라 부적합).
- 플랫폼 Deployment(backend/auth/frontend)에는 테넌트 템플릿과 달리 `topologySpreadConstraints`/anti-affinity 가 없어, 멀티 노드로 가도 replica 가 한 노드에 몰릴 수 있다 (`platform/backend.yaml`, `auth/auth-service.yaml`, `platform/frontend.yaml`).

### C-2. auth-db 가 단일 인스턴스 Deployment — 계정 단일 소스의 SPOF
- `deploy/k8s/auth/auth-db.yaml:39` — `replicas: 1` + RWO PVC. 계정 정보의 단일 소스인 DB 가 파드/노드 장애 시 **전체 로그인·토큰 갱신 불능**. 백업·복제·장애조치 전무.
- 플랫폼 DB 는 CNPG(HA) 경로가 있는데 auth-db 만 빠져 있다. **auth-db 도 CNPG Cluster 로 전환**해야 한다.

### C-3. DB 사이징·백업이 사실상 없음
- `deploy/k8s/platform/postgres-ha.yaml` — CNPG 3-인스턴스지만 `storage.size: 1Gi`, `limits: cpu 500m / mem 512Mi`. 수십만 계정 + 프로그램/알림/리뷰로그/refresh_tokens 증가 테이블에는 자릿수가 다르다.
- CNPG Cluster 에 **`backup`(barman objectStore)·ScheduledBackup·PITR 설정이 없다.** 장애·오조작 시 복구 수단 부재. 오브젝트 스토리지 기반 백업은 필수.
- 읽기 부하 분산 미사용: CNPG 가 `edu-db-ro`(replica 읽기) 서비스를 제공하는데 backend 는 `edu-db-rw` 만 사용 (`platform/backend.yaml:27`). 조회 중심 워크로드에서 읽기 replica 활용이 없다.
- **PgBouncer 등 커넥션 풀러 부재**: CNPG `Pooler` 리소스 미사용. 아래 C-4·H-2 와 결합하면 Postgres `max_connections` 고갈이 현실적 위험.

### C-4. 배포 파이프라인이 DB 트랜잭션 안에서 최대 640초 커넥션 점유
- `backend DeploymentService.deploy()` 가 `@Transactional`(`DeploymentService.java:138`) 인 채로 내부에서 `git clone`(120s, `SourceResolver.java:74-75`)·Kaniko 빌드 대기(640s, `DeploymentService.java:179-180`)·`kubectl apply`(120s) 를 실행한다. **그 전 시간 동안 Hikari 커넥션 1개를 트랜잭션으로 점유.**
- Hikari 풀이 기본값 10 (설정 전무, `backend/src/main/resources/application.yml`) 이므로 **동시 배포 10건이면 해당 인스턴스의 조회 트래픽까지 전부 마비**된다. 외부 프로세스 대기를 트랜잭션 밖으로 빼고, 상태 갱신만 짧은 트랜잭션으로 커밋해야 한다.

### C-5. 카탈로그 조회 경로 — 페이지네이션 없음 + N+1 + 자바 메모리 검색 + 캐시 없음
- 목록 API 가 공개 프로그램 **전건을 로드한 뒤 자바 스트림으로 필터·검색·정렬** (`ProgramService.java:51-59`), 검색은 자바 `contains` (`:183-191`) — DB 인덱스 자체를 못 탄다.
- `toSummary` 에서 LAZY `@ElementCollection`(tags/tech/purposes/run) 접근 → **프로그램 N건 × 컬렉션 수만큼 쿼리 (전형적 N+1)** (`ProgramService.java:211`, `Program.java:64-104`).
- 알림도 사용자 알림 전건 로드 후 자바에서 unread 카운트 (`NotificationService.java:28,33`).
- 캐시 계층 전무(Redis/`@Cacheable`/HTTP 캐시 없음). 카탈로그는 로그인 없이 공개(`/api/catalog/**` permitAll)라 **읽기 부하가 그대로 DB 에 직격**한다.
- 필요: `Pageable` 페이지네이션, DB 인덱스 기반 검색(또는 검색엔진), fetch join/`@BatchSize`/DTO 프로젝션, 카탈로그 응답 캐시(Redis 또는 CDN/HTTP 캐시).

### C-6. 로그인 경로 무방비 — rate limit·계정 잠금 전무 + bcrypt CPU DoS
- `auth-service` 의 `/login`·`/demo`·`/refresh` 가 전부 `permitAll`(`SecurityConfig.java:53-55`) 이고 **시도 횟수 제한·계정 잠금·IP rate limit 이 코드 어디에도 없다** (`AuthService.java:46-56`).
- 플랫폼 Ingress(`platform/ingress.yaml`)에도 rate-limit 주석이 없다(테넌트 템플릿에는 있음). bcrypt 는 요청당 수십~수백 ms CPU 를 쓰므로, 크리덴셜 스터핑이 아니어도 **개학일 아침 동시 로그인 폭주만으로 auth-service CPU 가 포화**될 수 있다.
- 필요: 엣지(rate-limit 주석 또는 WAF 규칙) + 애플리케이션(계정/IP 단위 제한) 이중 방어, 로그인 실패 지연.

### C-7. 데모 로그인이 운영 기본 활성(fail-open) + 운영 매니페스트에 시드 계정
- 데모 로그인은 **비밀번호 없이 역할 코드만으로 admin 토큰까지 발급**하는데, 기본값이 `true` (`DemoProperties.java:18`, `application.yml:39`). 운영 배포에서 `EDU_DEMO_LOGIN=false` 를 누락하면 그대로 열린다. **기본값을 false(fail-safe)로 역전**해야 한다.
- K8s 운영 매니페스트에 `EDU_SEED: "true"` 가 박혀 있다 (`platform/backend.yaml:38-39`, `auth/auth-service.yaml:61-62`) — 공통 임시 비밀번호를 가진 데모 계정 7개가 운영 DB에 생성된다.

---

## 3. 높음(High) — 규모 확대 시 조기에 부딪히는 문제

### H-1. 스키마 관리: 양쪽 모두 `ddl-auto: update`
- backend·auth-service 둘 다 (`backend application.yml:10`, `auth application.yml:10`). 다중 replica 동시 기동 시 스키마 변경 경쟁, 인덱스 추가 불가(엔티티에 선언된 것만), 롤백 불가. **Flyway/Liquibase + `validate`** 로 전환 필수. (주석에도 "추후 전환" 이라고 스스로 명시돼 있다.)

### H-2. 커넥션 풀·스레드 풀 튜닝 전무 (양쪽 서비스 공통)
- Hikari(기본 10)·Tomcat(기본 200) 설정이 양쪽 `application.yml` 에 전혀 없다. replica 수 × 풀 크기가 Postgres `max_connections`(기본 100) 와 정합되도록 산정 + PgBouncer(C-3) 필요. 부하 테스트 기반 산정 이력이 없다.

### H-3. DB 인덱스 미정의 (backend)
- 조회 조건 컬럼(`Program.status`, `Deployment.programId`, `Notification.toUser`, `Comment.programId` 등)에 인덱스 선언이 없다. `slug` unique 만 존재 (`Program.java:31`). 데이터 증가 시 순차 스캔. (auth-service 쪽 인덱스는 잘 되어 있음.)

### H-4. refresh_tokens 무한 팽창
- 만료·폐기 토큰 정리 쿼리 `deleteExpired` 가 **정의만 있고 호출처가 없다** (`RefreshTokenRepository.java:19-21`, `@Scheduled` 부재). 수십만 사용자 × 14일 토큰 × 회전마다 INSERT → 테이블·스토리지·VACUUM 부담 무한 증가. 정리 스케줄러/배치 필요.

### H-5. 배포 파이프라인 임시 파일 미정리 (디스크 누수)
- 클론 디렉터리(`SourceResolver.java:72`)·Kaniko/manifest 임시 파일(`DeploymentService.java:172,196`) 삭제 코드가 없다. 배포 횟수에 비례해 노드 디스크가 소진된다. `finally` 정리 필요.

### H-6. 이미지 태그 `:latest` 고정
- 플랫폼 3종 이미지 모두 `:latest` (`platform/backend.yaml:21` 등). 어떤 버전이 돌고 있는지 식별 불가, 롤백 불가, 파드 재스케줄 시 의도치 않은 버전 변경. **불변 태그(커밋 SHA/semver) + CI 파이프라인** 필요.

### H-7. Graceful shutdown 미구성 (양쪽 Dockerfile)
- `ENTRYPOINT ["sh","-c","java -jar app.jar"]` — java 가 PID 1 이 아니어서 SIGTERM 이 JVM 에 전달되지 않을 수 있다 (`backend/Dockerfile:22`, `auth-service/Dockerfile:17`). HPA 축소·롤링 업데이트가 상시 일어나는 규모에서는 **스케일 인 때마다 진행 중 요청 유실**. `exec` 형태 ENTRYPOINT + `server.shutdown=graceful` + preStop 지연 필요.
- JVM 힙 옵션(`-XX:MaxRAMPercentage` 등)도 전무 — 컨테이너 메모리 limit 과 정합 없이 기본값 의존, OOMKilled 위험.

### H-8. 시크릿 관리 — 자리표시자 Secret 이 매니페스트에 커밋
- `postgres.yaml`, `auth-db.yaml`, `auth-service.yaml` 에 `change-me-in-real-env` Secret 이 포함되어 `kubectl apply` 만으로 그대로 운영에 올라갈 수 있다(교체는 수동 절차에 의존). 정부기관 규모라면 **ExternalSecrets/SealedSecrets/Vault 등 외부 시크릿 관리 + 주기적 회전** 체계가 필요하다.
- JWT 가 HS256 공유 시크릿이라 검증하는 서비스가 늘수록 시크릿 배포 범위가 넓어지고 유출 시 전 서비스 영향 (`JwtTokenProvider.java:32,72`). 서비스 수가 늘기 전에 **RS256/ES256 + JWKS** 전환을 검토할 것.

---

## 4. 중간(Medium) — 운영 품질/여유 용량

| # | 항목 | 내용 |
|---|---|---|
| M-1 | 정적 자산 서빙 | `frontend/nginx.conf` 에 gzip/brotli·`Cache-Control`(해시 자산 immutable) 설정이 없다. 수십만 사용자면 CDN(또는 최소한 캐시 헤더+압축) 필요. |
| M-2 | 배포 워커 처리량 | 인스턴스당 3초에 1건 폴링 (`DeployWorker.java:33`, poll 3000ms). 배포 수요가 몰리면 큐 적체. 폴링당 배치 처리·전용 워커 Deployment 분리(사용자 API 와 스케일 축 분리) 권장. 재시도에 백오프 없음. |
| M-3 | HPA 신호 | CPU 70% 단일 지표. 배포 큐 길이·p95 지연 같은 커스텀 지표가 없다. 배포 큐 길이는 메트릭으로도 노출되지 않는다. |
| M-4 | ResourceQuota 사이징 | `edu-services` requests.cpu 8 / `public` 4 (`hardening/10-…`) — 테넌트 서비스가 수십 개로 늘면 즉시 부족. 규모 산정 재검토 필요(구조 자체는 올바름). |
| M-5 | 관측성 저장소 | Loki PVC 20Gi 단일 인스턴스 (`logging/loki-values.yaml`, 파일 주석에도 "실서버는 오브젝트 스토리지 권장" 명시). Tempo 동일 계열. 보존 기간 정의 없음. |
| M-6 | `/actuator/**` 전체 permitAll | 스크레이프 대상(`/actuator/prometheus`)만 열도록 축소 권장 (`backend SecurityConfig.java:48`). |
| M-7 | 시드 동시 기동 경쟁 | `DataSeeder.java:66-73` — 빈 DB에 replica 동시 부팅 시 count 체크와 insert 사이 경쟁으로 중복 시드 가능. 초기화 Job 분리 권장. |
| M-8 | Gitea 단일 replica + SQLite + 메모리 세션 | 내부 코더용이라 당장은 허용 가능하나(values.yaml 주석에 인지됨), 운영 전환 시 계획대로 CNPG 외부 DB 전환 + 백업 일원화 필요. |
| M-9 | bcrypt cost 미지정 | 기본 strength 10 의존 (`auth SecurityConfig.java:41`). 명시 지정 + 로그인 CPU 용량 산정에 반영. |

---

## 5. 권장 조치 순서

**0단계 — 코드 수정만으로 가능한 것 (인프라 불변)**
1. `DeploymentService.deploy()` 트랜잭션 분리 (C-4)
2. 카탈로그 페이지네이션 + N+1 제거 + DB 검색/인덱스 (C-5, H-3)
3. 로그인 rate limit/계정 잠금 (C-6) · 데모 로그인 기본값 false (C-7)
4. refresh_tokens 정리 스케줄러 (H-4) · temp 파일 정리 (H-5)
5. Hikari/Tomcat 명시 설정 (H-2) · Flyway 전환 (H-1) · Dockerfile ENTRYPOINT/JVM 옵션 (H-7)

**1단계 — 클러스터/데이터 계층**
6. 멀티 노드 클러스터(또는 매니지드) + L4 LB + 노드 독립 스토리지 (C-1)
7. auth-db CNPG 전환 (C-2) · CNPG 사이징 현실화 + barman 백업/PITR + Pooler(PgBouncer) (C-3)
8. Redis(캐시·rate limit 카운터 공유) 도입 (C-5, C-6)
9. 외부 시크릿 관리 + 이미지 불변 태그 CI (H-6, H-8)

**2단계 — 규모 검증**
10. 목표 부하 정의(피크 동접·RPS) 후 부하 테스트로 풀·스레드·HPA·Quota 수치 확정 (H-2, M-4)
11. 읽기 replica(`edu-db-ro`) 활용, CDN/캐시 헤더 (C-3, M-1)
12. 배포 워커 분리·큐 메트릭·커스텀 HPA (M-2, M-3)

---

## 부록 — 용량 감각 (대략치)

- 20만 계정, 피크 동접 5%(1만 명), 사용자당 분당 6요청 가정 → **피크 약 1,000 RPS**. 카탈로그 조회가 대부분이므로 캐시 적중 시 backend 2~10 replica 로 감당 가능한 수준 — 즉 **병목은 애플리케이션 replica 수가 아니라 DB(풀·인덱스·전건 조회)와 단일 노드**다.
- 로그인 폭주(출근 시간 30분에 10만 로그인 = 약 55 TPS × bcrypt ~100ms)만으로 auth-service 수 코어가 필요 — rate limit 과 수평 확장 산정이 선행되어야 한다.
