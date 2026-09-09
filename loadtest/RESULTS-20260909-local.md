# 부하 테스트 결과 — 2026-09-09 / 로컬 단일 인스턴스 (28코어·32GB, 생성기 동일 머신)

- 구성: backend·auth JAR 각 1 (-Xmx1g) + postgres:16 ×2 + redis:7 (전용 컨테이너, 운영 데이터 무접촉)
- 시드: 계정 5만(lt_user_*) · 프로그램 2,000(lt-prog-*) · Flyway V1 실 Postgres 적용 확인
- 원본: `loadtest/results/20260909-local/` (summary.json + local-metrics.csv + k6.log)
- **환경 한계**: 생성기와 서버가 같은 머신(고 RPS에서 CPU 공유), 단일 인스턴스라
  replica/HPA/ResourceQuota 는 미측정 — 해당 항목은 무변경, 산술 권장만 기록.

## 하네스 수정 이력

- k6 기본값(iteration 마다 쿠키 리셋)이 refresh 회전 체인을 끊어 401 누적 →
  refresh IP 차단(429) 발동(방어 정책 정상 동작 확인). `noCookiesReset: true` 로 수정,
  AUTH_URL 분리(로컬 포트 분리 대응).

## 단계별 측정 (mixed.js, DURATION 2m)

| 단계 | 목표 RPS | 실측 RPS | p50 | p95 | p99 | 오류율 |
|---|---|---|---|---|---|---|
| rps100 | 100 | 110.8 | 1.5ms | 7.2ms | 51ms | 0% |
| rps500 | 500 | 552.8 | 1.0ms | 5.2ms | 49ms | 0% |
| **rps1000 (1차 기준)** | 1,000 | 1,105.9 | 1.0ms | 5.1ms | 49ms | 0% |
| rps2000 | 2,000 | 2,209.7 | 1.0ms | 5.7ms | 54ms | 0% |

- 전 구간 p99 ~50ms 는 로그인(bcrypt ~47ms) 꼬리. catalog_list p95 1.1~1.6ms.
- rps2000 리소스: backend CPU ≤6.4% · Hikari active 2~3/20 · pending 0 · pg 21 conn ·
  Redis 적중률 91.3%(final 런 구간 delta 기준).

## 로그인 폭주 (login-burst.js — auth 단일 인스턴스 한계 탐색)

| BURST_RPS | 실측 처리 | p50 | p95 | p99 | dropped | 관측 |
|---|---|---|---|---|---|---|
| 100 | 100/s | 47ms | 50ms | 53ms | 0 | CPU 17%(≈4.8코어) — bcrypt 47ms/건 |
| 300 | 300/s | 53ms | 57ms | 60ms | 0 | CPU 60% · **Hikari active 15~16/20** |
| **450 (baseline)** | **322/s 두절** | 9,321ms | **12,018ms** | 12,060ms | **9,596** | **active 20/20 · pending 179 — 풀 고갈** |

## 병목 특정

1. **auth 커넥션 풀 고갈(1순위)** — `login()` 이 `@Transactional` 이라 bcrypt(~50ms) 동안
   커넥션 점유 → 필요 커넥션 ≈ 로그인RPS × 0.05. 풀 20 은 ~400/s 에서 고갈(실측).
2. **auth bcrypt CPU(2순위)** — 코어당 ~21 로그인/s (47ms/건). 풀 해소 후 450/s 에서 CPU 92~96%.
3. 조회 경로는 2,000 RPS 에서도 병목 없음(캐시 91% 적중, DB active ≤3).

## 튜닝 실험 (한 번에 1개 · 동일 부하 재실행 비교)

| # | 변경 | 부하 | 변경 전 | 변경 후 | 판정 |
|---|---|---|---|---|---|
| 1 | auth `DB_POOL_MAX_SIZE` 20→**40** | burst450 | p95 12,018ms · pending 179 · drop 9,596 | **p95 62ms · pending 0 · drop 0** (active 피크 39/40) | **채택** |
| 2 | backend `DB_POOL_MAX_SIZE` 20→**10** | rps1000 | p95 5.1ms · active ≤3/20 | p95 5.2ms(steady p95 1.6ms) · active ≤2/10 · pending 0 | **채택**(동일 성능, 총량 여유 확보) |
| 3 | auth `TOMCAT_THREADS_MAX` 200→64 | burst450 | p95 62ms / p99 77ms | p95 61ms / p99 74ms | **되돌림**(오차 범위) |

최종 확인 런(rps1000·3m, 최종 구성): 오류 0% · 전체 p95 5.2ms · catalog_list p95 1.1ms ·
Redis 적중률 91.3% · backend Hikari 피크 2/10 · auth 피크 1/40(정상 부하).

## 반영된 설정 (근거 포함 — 매니페스트 주석에 동일 기록)

| 항목 | 전 | 후 | 근거 |
|---|---|---|---|
| auth `DB_POOL_MAX_SIZE` (k8s env) | 20 | **40** | 실험 1. 2 replica×40=80 클라이언트(풀러 경유) |
| backend `DB_POOL_MAX_SIZE` (k8s env) | 20 | **10** | 실험 2. HPA max 10×10=100 ≤ 풀러 max_client_conn 1000 |
| auth 풀러 rw `default_pool_size` | 20 | **40** | 앱 풀 40 정렬(풀러 20 이면 그 지점에서 재병목) |
| CNPG `max_connections` (양 DB) | 기본 100 | **200** | 풀러 서버측 총량: auth 2×40+2×20=120 / platform 2×25+2×25=100 — 기본 경계 초과/일치 |
| Tomcat threads/connections | 200/8192 | 유지 | 실험 3 무효과·busy 낮음(실측) |
| Hikari minimum-idle | 5 | 유지 | 문제 신호 없음 |
| Redis 커넥션(lettuce) | 기본 | 유지 | 2,000 RPS 카운터·캐시 지연 징후 없음 |
| replica / HPA min·max·threshold / ResourceQuota / requests·limits | — | **무변경** | 로컬 단일 인스턴스 — 미측정 항목은 추측 변경 금지 원칙 |

## read replica 라우팅 실측 (12단계 — rps1000·2m, replica 는 pg_dump 로 동기화한 별도 인스턴스로 모사)

| 런 | 구성 | primary tuple reads | replica tuple reads | p95 | 오류율 |
|---|---|---|---|---|---|
| A | 캐시 ON · 라우팅 OFF | 51,211,528 | ~0 | 48ms* | 0% |
| B | 캐시 ON · 라우팅 ON | **47,533,032 (−7.2%)** | 1,151,171 | 8.4ms | 0% |
| C | 캐시 OFF · 라우팅 ON (Redis 장애 모사) | **47,483,692 (B 와 동일)** | **271,634,185 (85.1% 흡수)** | 270ms | 0.08% |

\* A 는 앱 콜드 기동 직후라 p95 에 JIT 워밍업 포함.

- 정상 운영(B): Redis 캐시가 1차로 91% 를 흡수하므로 replica 이동분은 캐시 미스 잔여(−7.2%).
- Redis 전면 장애(C): 카탈로그 전 트래픽이 replica 로 넘어가 **primary 읽기가 그대로 유지** —
  캐시(1차)·replica(2차)·primary(쓰기+일관성 필요 조회) 3계층 역할 분리가 수치로 입증됨.
- 복제 지연(lag)은 로컬 모사 환경에선 측정 불가 — CNPG 스테이징에서 재확인 필요.

## 다음 tuning 권장 (클러스터 측정으로 확정할 것)

| 항목 | 권장 | 산술 근거(실측 기반) |
|---|---|---|
| **auth-service HPA 신설** | min 2 / max 8 / CPU 70% | 현재 auth 는 HPA 자체가 없음. 코어당 ~21 로그인/s 실측 → limit 1c 파드 ≈ 20/s. 출근 폭주 300/s 목표 시 replica ≈ 15(1c) 또는 limit 2c×8 |
| auth resources | limit cpu 2 로 상향 검토 | bcrypt CPU-bound — 파드당 처리량이 limit 에 비례(실측 코어당 21/s) |
| 로그인 트랜잭션 분리(코드) | `login()` 의 bcrypt 를 @Transactional 밖으로 | 커넥션 점유 시간 50ms→수 ms 로 축소되면 풀 40 도 과잉이 됨 — 근본 해결 |
| backend HPA | 유지(max 10) | 1,000 RPS 에 backend CPU 3.4%(28코어 기준) — 1c limit 파드 환산 시에도 2 replica 로 충분 추정, 클러스터 실측으로 확정 |
| ResourceQuota | 유지 | 테넌트 워크로드 미포함 측정이라 판단 근거 없음 |
