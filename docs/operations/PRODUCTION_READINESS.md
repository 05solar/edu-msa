# PRODUCTION_READINESS.md · Production 배포 전 최종 점검

> 점검일: 2026-09-10 · 기준 커밋: `d85ce9b`(main) + 본 점검에서 커밋된 수정
> 대상: v0.8.0 확장성 개조 완료본 · staging = kind 멀티노드(cp1+worker3, Calico)
> **판정: CONDITIONAL GO** (§10) — Production 배포는 수행하지 않음(readiness review까지만).

---

## 1. 점검 중 실측·수정 요약

이 점검에서 **staging 에 실제로 수행·확인한 것**:

| 항목 | 결과 |
|---|---|
| backend 테스트 (Docker gradle build) | 통과 (19 tests) |
| auth-service 테스트 (로그인 트랜잭션 수정 포함) | 통과 (15 tests) |
| frontend production 빌드 (tsc + vite) | 통과 |
| kubeconform (플랫폼·auth·HA 매니페스트, CRD 스키마 포함) | 35 리소스 Valid |
| k6 smoke (login/list/counts/refresh) | 4/4 통과 |
| ID/PW(bcrypt)·데모 로그인·refresh 회전·오답 401 | 전부 정상 (신규 auth 이미지) |
| **CNPG failover 드릴** — edu-db primary 파드 삭제 | **자동 승격 183s(edu-db-2→1), 3/3 재수렴 224s** |
| **PITR 복구 드릴** — MinIO 백업에서 targetTime 시점 복구 | **성공** — programs 2,008건 복원, 목표 시각 이후 커밋 미반영(시점 정확), Flyway V1·V2 스키마 온전 |
| **stale replica 재클론** — 콜드리스타트로 timeline 분기된 auth-db-3 | PVC+파드 삭제 → 오퍼레이터 재클론 → 3/3 healthy |
| 백업 파이프라인 | ContinuousArchiving=True, ScheduledBackup 일일 실행 completed (MinIO) |
| replication lag | 유휴 기준 0s (cnpg_pg_replication_lag 실측) |
| **경보 파이프라인** | 규칙 13종 로드(health=ok), Prometheus→Alertmanager 연결 활성, 합성 알림 수신 확인 |
| 로그인 burst 30/s (단일 IP) | 엣지 limit-rps 5 가 초과분 차단(5,645건), 통과분 1,512건 전원 200 · p95 59ms · Hikari active/pending 0 |
| HPA 스케일 다운 | 부하 해소 후 backend 6→2 복귀 확인 |

**이 점검에서 수정한 것**(기능 추가 없음 — 배선·관측 결함과 실측 근거 있는 최소 변경):

1. `auth-service AuthService.login()` — `@Transactional` 제거. bcrypt(~50ms)가 트랜잭션 안에서
   DB 커넥션을 점유해 400 로그인/s 에서 풀 고갈(11단계 실측: active 20/20·pending 179·p95 12s)
   이 관측된 구조적 원인 제거. 조회는 리포지토리 자체 짧은 읽기 트랜잭션, 토큰 저장은 `save()`
   자체 짧은 쓰기 트랜잭션으로 분리. `refresh()`는 회전·탈취 감지의 원자성이 필요해 유지.
   검증: 단위 테스트 15종 + staging 배포 후 전 로그인 경로 실검증.
2. `prometheus-rules.yaml` — 라벨 `release: kps` → `monitoring` (스택 릴리스명과 불일치로
   **규칙이 전혀 선택되지 않던 결함**, 6431dff 의 ServiceMonitor 결함과 동일 계열).
   Production 필수 alert 10종 추가(§8).
3. `bootstrap.sh` — kube-prometheus-stack 에 `alertmanager.enabled=true` 명시(과거 수동 설치가
   false 로 남긴 릴리스 교정) + `podMonitorSelectorNilUsesHelmValues=false`(CNPG PodMonitor 에
   release 라벨이 없어 **DB 메트릭이 전혀 수집되지 않던 결함**). 규칙 적용의 `2>/dev/null || true`
   무음 실패 제거(실패 시 경고 출력).

---

## 2. Staging 검증 결과 분류

측정 환경 3층 구분: **로컬 JAR**(RESULTS-20260909-local.md — K8s 아님) / **staging**(kind 멀티노드,
09-09~10) / **구 kind 단일노드**(08-25, P0~P3). 실측 없는 항목은 미검증으로 명시.

| 항목 | 판정 | 근거 |
|---|---|---|
| 기본 기능 smoke | **통과** | staging k6 4/4 (09-09, 09-10 재확인) |
| login/JWT/refresh | **통과** | staging 실검증(ID/PW·데모·refresh 회전·오답 401) + 로컬 부하 |
| Redis cache | **조건부 통과** | 적중률 91.3%·장애 폴백은 **로컬 실측**. staging 은 기동·배선만 확인 |
| read replica 라우팅 | **조건부 통과** | 라우팅 효과(85.1% 흡수)는 **로컬 pg_dump 모사** 실측 + H2 라우팅 단위테스트. CNPG ro 풀러 실라우팅의 부하 실측 없음 |
| CNPG failover | **통과** | 본 점검 드릴: primary 삭제→자동 승격 183s→3/3 재수렴 |
| replication lag | **조건부 통과** | 유휴 0s 실측(본 점검). 쓰기 부하 하 lag 미측정 |
| HPA | **통과** | staging 부하 2→10 스케일아웃(09-09) + 축소 6→2(본 점검) |
| KEDA | **통과** | staging 큐 12건→워커 1→4(20s)·소진 후 1 복귀(09-09) |
| worker job recovery | **조건부 통과** | requeueStale·백오프·drain 단위테스트 3종 통과. 클러스터 수준 강제종료 실측 없음 |
| 일반 API load | **조건부 통과** | rps100~2000 오류 0%·p95 5ms 는 **로컬 JAR** 실측. staging 은 HPA 유발 부하만 |
| login burst | **조건부 통과** | 100~450/s 는 **로컬** 실측(풀 40 채택 근거). staging 은 30/s(엣지 limit 동작 확인) |
| NetworkPolicy | **조건부 통과** | 정책 4종 staging 적용 확인. egress 차단 실측은 08-25 구 클러스터 기록 |
| backup | **통과** | staging 실백업 completed + WAL 연속 아카이브(MinIO) |
| PITR | **통과** | 본 점검 복구 드릴 성공(시점 정확성 포함) |
| ingress/TLS | **조건부 통과** | staging HTTP 경로·rate-limit 실측. TLS/WAF 는 08-25 기록(현 staging 은 TLS 미종단 구성) |
| metrics/logging | **조건부 통과** | metrics: 본 점검에서 전 타깃 up + CNPG 수집 개시. logging/tracing(Loki·Tempo)은 현 staging 미설치(08-25 검증 기록) |
| 테넌트 배포 E2E | **미검증(현 staging)** | 09-01 real 모드 전 구간·09-03 Gitea E2E 기록. 현 staging 엔 Gitea·테넌트 미기동 |

### PITR 드릴에서 얻은 운영 교훈 (Runbook 반영)

- 유휴 DB 는 WAL 세그먼트가 안 차서 **targetTime 이후 커밋 레코드가 아카이브에 없으면 복구가
  목표 도달을 판정하지 못한다**("recovery ended before configured recovery target was reached").
  실복구 시 target 은 **마지막 아카이브 WAL 범위 내**로 잡거나, targetTime 을 생략(최신까지 복구)한다.
- `archive_timeout=5min`(CNPG 기본)은 **활동이 있어야** 강제 전환한다. RPO 확인은
  `pg_stat_archiver.last_archived_time` 으로 상시 감시.

---

## 3. Critical 위험 판정 (배포 차단 조건 점검)

| Critical 조건 | 판정 | 근거 |
|---|---|---|
| 데이터 손실 가능성 | 없음 | 백업+PITR 실증, WAL 아카이브 동작 |
| DB failover 실패 | 없음 | 드릴 성공(183s) |
| PITR 복구 실패 | 없음 | 드릴 성공 |
| migration 실패 | 없음 | Flyway V1·V2 staging 적용·복원본 검증, SchemaMigrationTest |
| 인증 우회 | 없음 | 오답 401, demo/seed 운영 기본 off(fail-safe), JWT 32B 미만 부팅 거부 |
| Secret 노출 | 없음 | 매니페스트 placeholder 0, server 모드 fail-closed, Sealed Secrets 표준. 값 미출력 확인 |
| 주요 endpoint 5xx 반복 | 없음 | smoke·부하에서 5xx 0 |
| 고부하 pool 고갈 | 해소 | 로그인 트랜잭션 분리(본 점검) + 풀 40(실측 채택) + EduHikariPendingSustained 경보 신설 |
| worker job 유실·중복 | 낮음 | SKIP LOCKED + requeueStale(멱등) + 백오프 — 단위검증. 잔여: 동일 슬러그 동시 배포 직렬화(백로그 B) |
| NetworkPolicy 격리 실패 | 낮음 | default-deny 적용 확인(실차단 실측은 08-25 기록) |
| rollback 경로 없음 | 없음 | 불변 태그 + GHCR 릴리스 확인(digest §6) + rollout undo + PITR |
| observability 불능 | **해소(본 점검 수정)** | 규칙 미적용·라벨 불일치·CNPG 미수집·AM 꺼짐을 수정, 파이프라인 실검증 |

→ **Production 배포를 차단하는 Critical 미해결 항목 없음.** 단 §10 의 조건 이행 전제.

---

## 4. 로그인 트랜잭션·JWT 구조

### 로그인 트랜잭션 (수정 완료)
- 문제 실관측: bcrypt 가 `@Transactional login()` 안에서 실행 → 커넥션 점유 → 풀 20 이
  400/s 에서 고갈(로컬 실측, active 20/20·pending 179·p95 12s). 풀 40 완화 후에도 구조 원인 잔존.
- 수정: §1-1 참조(최소 변경 — login() 만, refresh 회전·탈취 감지 의미 불변).
- staging 확인: 신규 이미지 롤아웃(무중단) 후 전 로그인 경로 정상, 통과 로그인 p95 59ms,
  Hikari active/pending 0. (고부하 차별 실증은 로컬 400/s 실측이 근거 — staging 은 엣지
  limit-rps 5 로 그 수준 재현 불가)

### JWT (HS256 공유 시크릿) — **후속 개선으로 분류 (Production 전 필수 아님)**
- 검증 주체: auth-service(발급+검증)·backend(검증)·backend-worker(동일 이미지, HTTP 트래픽 없음).
  테넌트 서비스는 JWT 미사용 → **시크릿 배포 범위가 내부 2개 서비스로 한정**.
- 방어: 알고리즘 HS256 명시 고정(다운그레이드 차단), 32B 미만·빈값 부팅 거부(fail-closed),
  Sealed Secrets 관리, 회전 절차 문서화(secrets/README §회전).
- 한계: `kid`/JWKS 없음 → 무중단 회전 불가(회전 시 기존 Access Token 즉시 무효, 최대 30분 재로그인).
- 판단: 자원 서버가 2개인 현 구조에선 수용 가능. **JWT 검증 서비스가 추가되기 전에 RS256/ES256+JWKS
  전환 필수**(백로그 등재).

---

## 5. 환경변수·Secret

### 환경변수 (전수 대조 결과)
- manifest ↔ application.yml **이름 불일치(orphan) 없음** — backend 21개·auth 15개 주입 전부 소비 확인.
- 필수(Secret 주입): `DB_URL/USER/PASSWORD`(backend·worker), `AUTH_DB_URL/USER/PASSWORD`(auth),
  `EDU_JWT_SECRET`(3 워크로드 공통), `REDIS_HOST/PASSWORD`, `EDU_SEED_PASSWORD`(auth).
- 필수(일반 config): `EDU_DEPLOY_MODE=real`, `EDU_DEPLOY_REGISTRY`, `EDU_DEPLOY_INGRESS_HOST`,
  `CORS_ORIGINS`, `EDU_COOKIE_SECURE=true`, `EDU_SEED=false`, `EDU_DEMO_LOGIN=false`(auth),
  `EDU_DEPLOY_WORKER_ENABLED`(API false/worker true), `DB_POOL_MAX_SIZE`(10/40/5), `DB_RO_URL`(backend).
- 선택(optional Secret): `edu-gitea-token`·`edu-gitea-webhook`(Gitea 미사용 시 없어도 기동).
- 기본값 사용 가능: Tomcat 4종, Hikari 미세 튜닝 3종, JWT TTL, rate-limit 정책, cleanup 정책,
  Flyway on, `EDU_DDL_AUTO=validate`, `EDU_CACHE_TYPE=redis` 등.
- 주의 2건: ① `EDU_JWT_ISSUER` 는 양쪽 모두 기본값 의존 — **한쪽만 override 하면 전면 인증 실패**
  (변경 시 동시 배포). ② rate-limit 정책 전부 소스 기본값 — NAT 환경 튜닝은 §10 조건 참조.

### Secret 공급 체크리스트 (값 미출력 — 존재·공급처만)

| Secret | 참조 워크로드 | Production 공급 | 확인 |
|---|---|---|---|
| `edu-db` (단일 DB 경로) | backend·worker (bootstrap 치환 시) | Sealed Secrets(봉인본 커밋) · **server 모드 부재 시 기동 중단(fail-closed)** | bootstrap `ensure_secrets` 코드 확인 |
| `edu-auth-db` (단일 경로) | auth | 동일 | 동일 |
| `edu-db-app` / `edu-auth-db-app` (HA 경로) | backend·worker / auth | **CNPG 오퍼레이터 자동 생성** | staging 실사용 중 |
| `edu-auth-jwt` (JWT+시드PW) | backend·worker·auth | Sealed Secrets · fail-closed | 32B 미만 부팅 거부 |
| `edu-redis-auth` | redis·backend·worker·auth | Sealed Secrets · fail-closed | staging 무작위 생성 사용 중 |
| `edu-db-backup-creds` (오브젝트 스토리지) | CNPG 백업 | 수동 생성(HA 적용 시) — **ensure_secrets 필수 목록에 없음 → HA 배포 절차에서 별도 확인 필요** | staging MinIO 로 동작 확인 |
| `edu-gitea-token`·`edu-gitea-webhook` | backend(·worker) | bootstrap stack 이 자동 생성 · optional | 미존재 시도 기동 정상 |
| TLS | ingress | cert-manager(실서버 ACME 전환 문서화) | staging 은 TLS 미종단 구성 |

**placeholder 로 기동되는 경로: K8s에는 없음**(매니페스트에서 placeholder Secret 전량 제거됨, 9단계).
compose/.env 경로는 로컬 개발 전용.

---

## 6. Production 이미지 (불변 태그 · `:latest` 금지)

CI(`release.yml`)가 main push·v* 태그마다 GHCR 로 push. **GHCR 릴리스 실존·digest 확인 완료**:

| Service | Image | Tag | Digest (git-d85ce9b) |
|---|---|---|---|
| backend | `ghcr.io/05solar/edu-msa/edu-msa-backend` | git-d85ce9b · 0.8.0 | `sha256:c6d8a1ac…88cb0f0` |
| auth-service | `ghcr.io/05solar/edu-msa/edu-msa-auth-service` | git-d85ce9b · 0.8.0 | `sha256:33ad092d…327494e` |
| frontend | `ghcr.io/05solar/edu-msa/edu-msa-frontend` | git-d85ce9b · 0.8.0 | `sha256:37f16506…61c54b4` |

**Production 확정 태그는 본 점검 수정 커밋의 `git-<sha>`** (push 시 CI 자동 빌드 — 로그인 트랜잭션
수정이 auth 이미지에 포함되어야 함). staging 현재: backend/frontend `git-0d9cfba`,
auth `review-login-txn`(본 점검 수정 반영 빌드).

---

## 7. 데이터 계층·리소스·커넥션 예산

### CNPG (HA 매니페스트 기준 — bootstrap core 는 단일 DB, HA 는 PRODUCTION.md §4-1 수동 적용)

| 항목 | edu-db (플랫폼) | edu-auth-db (인증) |
|---|---|---|
| instances / max_connections | 3 / 200 | 3 / 200 |
| storage | **50Gi** (staging 은 2Gi 축소본) | **20Gi** (staging 1Gi) |
| resources | 1c/2Gi → 2c/4Gi | 500m/1Gi → 2c/2Gi |
| 백업 | barman(S3)+WAL gzip, retention 30d, 일일 03:00 | 동일, 03:30 |
| Pooler rw/ro | transaction 모드, pool 25/25 ×2 inst | pool 40/20 ×2 inst |
| 배치 | anti-affinity preferred | 동일 |

주의: ① HA yaml 의 storageClass 는 순수 주석 — **bootstrap STORAGE_CLASS 치환이 안 걸리므로 수동
지정 필수**(노드 종속 스토리지 금지). ② Pooler 파드에 anti-affinity 없음(같은 노드 쏠림 가능).
③ endpointURL(`s3.edu.internal`)은 실서버 오브젝트 스토리지로 교체.

### Redis
- 단일 Deployment(비영속, maxmemory 256mb, allkeys-lru, requirepass, 비루트). **모니터링 exporter 없음.**
- 장애 전파: backend 는 LenientCacheErrorHandler 로 DB 폴백(로컬 장애 모사 실측 — replica 가 85.1%
  흡수, 오류 0.08%), auth rate-limit 은 인메모리 폴백(**fail-open 아님**, failover 카운터 + 신설
  경보 EduRedisFallbackActive 로 감지). → Redis 는 SPOF 아님. HA 필요 시 env 교체로 관리형/Sentinel.

### 리소스 (§Resource 표)

| Service | Replica | CPU Req/Limit | Mem Req/Limit | staging 관측 |
|---|---:|---|---|---|
| backend | 2 (HPA 2–10) | 250m / 1 | 512Mi / 1Gi | idle 450–480Mi — 적정 |
| backend-worker | 1 (KEDA 1–5) | 200m / 1 | 512Mi / 1Gi | 445Mi — 적정 |
| auth-service | 2 (고정, HPA 없음) | 200m / 1 | 384Mi / 768Mi | **idle 420Mi > request 384Mi — request 512Mi 상향 권장** |
| frontend | 2 (HPA 2–8) | 50m / 250m | 64Mi / 128Mi | 적정 |
| redis | 1 | 100m / 500m | 128Mi / 512Mi | 적정 |
| edu-db ×3 | 3 | 1 / 2 | 2Gi / 4Gi | staging 축소본으로 비교 불가 |
| edu-auth-db ×3 | 3 | 500m / 2 | 1Gi / 2Gi | 동일 |

### DB Connection Budget

| Service | Replica(max) | Hikari Pool | Max Client Conns |
|---|---:|---:|---:|
| backend (rw) | 10 | 10 | 100 |
| backend (ro) | 10 | 10 | 100 |
| backend-worker (rw) | 5 | 5 | 25 |
| auth-service (rw) | 2 | 40 | 80 |

- 클라이언트 → PgBouncer: 전 항목 `max_client_conn 1000` 이내 (최대 125 rw / 100 ro — 플랫폼).
- PgBouncer → PostgreSQL(서버측 실커넥션): 플랫폼 rw 2×25=50 + ro 2×25=50, 인증 rw 2×40=80 +
  ro 2×20=40. 각 클러스터 `max_connections 200` 대비 **플랫폼 primary 여유 ~140, 인증 ~110**
  (복제 2 + 오퍼레이터/프로브 ~6 + superuser 예약 감안). 관리·복제 몫 침범 없음.
- auth 에 HPA 신설 시 replica×40 재산정 필수(4 replica = 160 클라이언트 → rw pool 상향 검토).

### HPA/KEDA (실측 근거 유지 — 값 변경 없음)
- backend 2–10 @CPU70% (staging 2→10 실측) · frontend 2–8 @70% · worker KEDA 1–5, `max(edu_deploy_queue_depth)>3`/워커, cooldown 300s (staging 1→4 실측).
- auth: HPA 없음 — bcrypt 코어당 ~21 로그인/s(실측) 기준 용량 산정 후 신설 권장(백로그 "로그인 용량").

---

## 8. Monitoring/Alert (본 점검에서 배선 수리 + 필수만 추가)

기존 3종(EduBackendDown/PodCrashLooping/HighMemory) + 신설 10종 — 전부 staging 로드·health=ok 확인:

| Alert | 조건 | 심각도 |
|---|---|---|
| EduAuthDown / EduWorkerDown | up==0 2m/5m | critical/warning |
| EduHttp5xxHigh | 5xx 비율 >5% 5m | critical |
| EduHikariPendingSustained | pending >5 3m | critical |
| EduDbReplicationLagHigh | lag >30s 5m | warning |
| EduDbInstanceDown | 인스턴스 <3 10m | warning |
| EduDeployQueueBacklog | depth >30 10m | warning |
| EduDeployRetriesHigh | 재시도 >5/15m | warning |
| EduRedisFallbackActive | auth 폴백 카운터 증가 | warning |
| EduPvcAlmostFull | 잔여 <10% (kind 는 메트릭 미노출 — **실서버에서만 유효, staging 미검증**) | critical |

미커버(의도적 미추가 — 과잉 방지): p95 지연(히스토그램 버킷 미노출 — micrometer percentiles 설정
필요, 후속), Redis 직접 다운(exporter 없음 — 폴백 카운터로 간접 감지).
**잔여 필수 작업: Alertmanager 수신처(Slack/Email) 라우팅 — 현재 알림이 AM 까지만 도달.**

---

## 9. 운영 절차

### 9-1. Migration (Flyway)
- **신규 Production DB**: 첫 기동 시 V1(init)→V2(deploy job backoff) 자동 적용. baseline 불필요.
  다중 replica 동시 기동은 Flyway 잠금으로 직렬화되나, **최초 기동만 replica 1 로 시작 후 확장 권장**.
- **기존 DB 업그레이드**: `baseline-on-migrate=1` 이 V1 을 스킵(검증됨). 이후 버전부터 적용.
- **실행 전 필수**: on-demand 백업 생성 —
  `kubectl cnpg backup edu-db -n edu-platform` (또는 Backup CR apply) → completed 확인 →
  복구 목표 시각 기록. 실패 시: 앱 기동 실패(validate)로 안전 정지 → 원인 수정 재배포,
  스키마 오염 시 PITR(§9-3-C).
- rollback 관점: V1·V2 는 additive(테이블·컬럼·인덱스 추가) — **직전 앱 버전과 스키마 호환**,
  앱만 롤백 가능. 향후 파괴적 마이그레이션은 expand-contract 로 작성할 것.

### 9-2. 배포 순서 (신규 Production)
1. 클러스터 전제 확인 — 워커 3+·HA 컨트롤플레인(또는 매니지드)·L4 LB·네트워크 스토리지 CSI·
   Calico/gVisor (PRODUCTION.md §1)
2. 오퍼레이터·엣지 — CNPG 오퍼레이터, ingress-nginx(WAF), cert-manager(+ACME 발급자), KEDA
3. Secret 반입 — Sealed Secrets 컨트롤러 → 봉인본 apply(edu-db·edu-auth-db·edu-auth-jwt·
   edu-redis-auth) + **edu-db-backup-creds**(HA 백업용 — bootstrap 검사 밖이므로 수동 확인)
4. DB — `postgres-ha.yaml`·`auth-db-ha.yaml` apply (storageClass **수동 지정**) → 3/3 healthy →
   백업 아카이브 동작 확인(`ContinuousArchiving=True`)
5. ScheduledBackup 확인 + **PITR 드릴 1회**(본 점검 절차 재사용 — 복구 클러스터 생성·검증·삭제)
6. Redis (`redis.yaml`)
7. auth-service (JWT Secret 공유 관계상 backend 보다 먼저) → rollout 완료 대기
8. backend → 9. backend-worker (+KEDA ScaledObject) → 10. frontend
11. ingress (`ingress.yaml` — /api/auth 분리 rate-limit 포함, DOMAIN 치환)
12. autoscale (HPA/PDB) 13. monitoring — kube-prometheus-stack(bootstrap 플래그 반영본) +
    prometheus-rules + servicemonitor → 타깃 up·규칙 로드 확인
14. smoke test (§9-4) 15. (선택) Gitea 스택 — bootstrap stack 절차
- bootstrap core 를 쓰는 경우: **단일 DB 로 배선됨** — HA 는 PRODUCTION.md §4-1 전환 절차 필수.

### 9-3. Rollback
**A. Application 오류**
1) `kubectl rollout undo deploy/<svc> -n edu-platform` (직전 리비전 즉시 복귀) 또는
2) `IMAGE_TAG=git-<이전sha>` 로 재배포(불변 태그 — GHCR 에 전 버전 잔존). 3) rollout status·smoke 확인.

**B. Flyway migration 이후 오류**
1) V1·V2 급(additive)이면 앱만 A 절차로 롤백(스키마 호환 — validate 는 기존 컬럼만 검사).
2) 파괴적 변경이 섞였으면 앱 롤백 불가 → 전진 수정(fix-forward) 우선.
3) 데이터 오염 시: §C PITR 로 migration 직전 백업 시점 복구 + 앱 구버전.

**C. DB 장애**
1) primary 파드 장애: 자동 failover(드릴 검증 183s) — 개입 불요, `kubectl get cluster` 로 확인.
2) replica 이탈(timeline 분기 등): 해당 인스턴스 PVC+파드 삭제 → 오퍼레이터 재클론(드릴 검증).
3) 클러스터 전손: 신규 Cluster 에 `bootstrap.recovery`(barmanObjectStore, 필요 시 targetTime) —
   드릴 검증 절차 그대로. 복구 클러스터 확인 후 앱 DB 엔드포인트 전환.
   주의: targetTime 은 마지막 아카이브 WAL 범위 내로(§2 교훈).

**D. Secret 오류**
1) 이전 SealedSecret 봉인본(git 이력) 재적용 → 오퍼레이터가 Secret 갱신.
2) `kubectl rollout restart deploy/auth-service deploy/backend deploy/backend-worker` (JWT 등 공유
   Secret 은 **동시 재기동** — 한쪽만 갱신 시 전면 인증 실패).
3) JWT 회전 부작용: 기존 Access Token 최대 30분 무효 — 공지 후 실행.

### 9-4. Production Smoke Test (비파괴)
```
1. GET  https://<domain>/                 → 200 (frontend)
2. GET  /api/health                       → {"status":"ok"}
3. GET  /api/auth/health                  → {"status":"UP"}
4. POST /api/auth/login (스모크 계정)      → 200 + Set-Cookie(refresh)
5. POST /api/auth/refresh (쿠키)          → 200 (회전)
6. POST /api/auth/login (오답)            → 401
7. GET  /api/programs?page=0&size=5 (토큰) → 200 페이지 응답
8. GET  /api/programs/counts              → 200 분야별 집계
9. GET  /api/notifications?to=<계정>       → 200
10. Prometheus: up{job=~"backend|auth-service|backend-worker"} 전부 1,
    edu_deploy_queue_depth 존재, cnpg_pg_replication_lag < 10
11. kubectl get cluster -A → 전부 healthy · ContinuousArchiving=True
12. (배포 요청은 스테이징 전용 — production smoke 에선 큐 메트릭 확인까지만)
```
자동화: `loadtest/k6/smoke.js` (BASE_URL 지정, 1 VU) — 4/4 통과 기준.

### 9-5. 장애 Runbook (요약)

| 장애 | 확인 | 1차 조치 | rollback 조건 |
|---|---|---|---|
| backend 5xx 증가 | EduHttp5xxHigh · `kubectl logs deploy/backend` · Grafana | 최근 배포면 rollout undo, 아니면 DB/Redis 의존 확인 | 배포 후 10분 내 5xx>5% 지속 |
| auth 로그인 장애 | EduAuthDown/429·503 비율 · auth 로그 | rate-limit 오차단(NAT) 여부 → `EDU_RATELIMIT_*`/ingress limit 상향, bcrypt CPU 포화 → replica 증설 | 직전 auth 배포가 원인일 때 undo |
| DB 커넥션 고갈 | EduHikariPendingSustained · `pg_stat_activity` | 점유 쿼리 식별·kill, 풀 상향은 서버 예산(§7) 내에서 | — (구조 문제면 fix-forward) |
| PG primary 장애 | EduDbInstanceDown · `kubectl get cluster` | 자동 failover 대기(수분) → 미승격 시 CNPG 로그·operator 확인 | 전손 시 §9-3-C 복구 |
| Redis 장애 | EduRedisFallbackActive · redis 파드 | 재기동(Recreate 전략, 캐시 재적재) — 서비스는 DB 폴백으로 지속 | 불요(비영속) |
| worker 큐 폭증 | EduDeployQueueBacklog · retries | 실패 원인(레지스트리/Gitea/Kaniko) 제거 — KEDA 가 5까지 자동 증설 | — |
| OOMKilled | EduHighMemory · describe pod | limit 대비 사용 분석 → limit·`MaxRAMPercentage` 정합 조정 | 직전 배포로 증가 시 undo |
| ImagePullBackOff | describe pod | 태그 오타/GHCR 자격/레지스트리 가용성 — 불변 태그라 재현성 있음 | 이전 태그로 재배포 |
| ingress 502/504 | 컨트롤러 로그 · 백엔드 readiness | endpoint 유무 → readiness 실패 원인 우선 | 앱 원인이면 undo |
| 인증서 문제 | cert-manager 로그 · Certificate Ready | 발급자(ACME) 상태·DNS·rate limit 확인, 갱신은 자동 | — |

---

## 10. 최종 판정 — **CONDITIONAL GO**

**GO 근거**: §3 Critical 전 항목 해소 — 데이터 보호(백업+PITR+failover 실증), 인증(fail-safe·
fail-closed 실증), 확장(HPA/KEDA 실측), 롤백 경로(불변 태그+GHCR+undo+PITR), 관측성(본 점검
수리·실검증), 테스트·빌드·매니페스트 전부 통과.

**CONDITIONAL 사유 (배포 전 이행 조건)**:
1. **실서버 리허설** — staging 은 kind. PRODUCTION.md 전제(멀티노드 물리/매니지드, L4 LB, CSI
   스토리지, ACME, WAF)에서 §9-2 순서로 1회 리허설 + smoke 통과. (특히 TLS/WAF 는 현 staging 미종단)
2. **Alertmanager 수신처 구성** — 현재 알림이 AM 내부까지만 도달. Slack/Email 라우팅 없이는
   경보가 사람에게 닿지 않음.
3. **NAT 대비 rate-limit 튜닝** — staging 실측에서 엣지 limit-rps 5(IP당)가 단일 IP 30/s 중 25/s
   차단. 교육청·학교는 NAT 공유 IP 다수 — 출근 로그인이 기관 IP 당 5/s 를 넘으면 정상 사용자
   차단. limit-rps 상향(예: 기관 규모 산정 후 20~50) 또는 limit-burst 병용 필수.
   (앱 계층 IP 제한은 실패 기준이라 정상 로그인엔 미발동 — 실측 확인)
4. **본 점검 수정 커밋의 이미지로 확정** — auth 로그인 트랜잭션 수정이 포함된 `git-<sha>` 태그
   (CI 자동 빌드) 를 배포 대상으로.

### Remaining Risks

| 우선순위 | 문제 | Production 영향 | 대응 |
|---|---|---|---|
| High | Alertmanager 수신처 없음 | 장애를 사람이 모름 | 조건 2 — 배포 전 |
| High | NAT 환경 엣지 rate-limit 5/s | 기관 단위 로그인 차단 | 조건 3 — 배포 전 |
| Medium | auth HPA 부재(고정 2) | 로그인 폭주 시 수동 개입(코어당 ~21/s 실측) | 용량 산정 후 HPA 신설(백로그) |
| Medium | HA 전환·storageClass 수동 절차 | 절차 누락 시 단일 DB/노드종속 스토리지로 기동 | §9-2 체크리스트화, 리허설에서 확인 |
| Medium | 테넌트 배포 E2E 가 현 staging 미실행 | 배포 파이프라인 회귀 미탐지 | 리허설에 등록→배포 E2E 포함 |
| Medium | Loki/Tempo 현 staging 미설치 | 로그·트레이스 부재(경보는 동작) | 리허설에서 스택 설치·확인 |
| Low | JWT HS256 무중단 회전 불가 | 회전 시 30분 재로그인 | 자원서버 추가 전 RS256+JWKS |
| Low | Redis 단일 인스턴스·exporter 없음 | 캐시 성능 저하(폴백 실증됨) | 관리형/Sentinel + exporter 후속 |
| Low | Pooler anti-affinity 없음 | 노드 장애 시 풀러 동시 손실 가능 | HA 적용 시 topologySpread 추가 |
| Low | 동일 슬러그 동시 배포 직렬화 미구현 | 드문 Kaniko Job 경합 | 백로그 B 유지 |

---

## 갱신 이력
- 2026-09-10 — 최초 작성(Production readiness review 결과). 판정 CONDITIONAL GO.
