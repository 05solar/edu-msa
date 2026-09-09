# 부하 테스트 결과 — <날짜> / <환경: kind|스테이징(노드 수·스펙)>

- 이미지 태그: `git-<sha>` · 시드: 계정 <N> · 프로그램 <N> · 생성기: <머신 스펙, 대상 밖 여부>
- 결과 원본: `loadtest/results/<디렉터리>/`

## 단계별 측정 (mixed.js)

| 단계 | 목표 RPS | 실측 RPS | p50 | p95 | p99 | 오류율 | 비고 |
|---|---|---|---|---|---|---|---|
| rps100 | 100 | | | | | | |
| rps500 | 500 | | | | | | |
| rps1000 (1차 기준) | 1000 | | | | | | |
| rps2000 | 2000 | | | | | | |

## 엔드포인트별 p95 (rps1000 기준)

| 엔드포인트 | p95 | p99 | 오류율 |
|---|---|---|---|
| catalog_list | | | |
| catalog_search | | | |
| program_detail | | | |
| catalog_counts | | | |
| noti_list / noti_unread | | | |
| auth_refresh | | | |
| auth_login | | | |

## 로그인 폭주 (login-burst.js, BURST_RPS=<N>)

| 항목 | 값 |
|---|---|
| login p95 / p99 | |
| 429(rate_limited) 발생 수·시점 | |
| auth-service CPU 피크 / HPA replica 추이 | |

## 리소스·풀 (rps1000 steady 구간)

| 항목 | backend | auth-service | postgres | redis |
|---|---|---|---|---|
| CPU 피크(m) | | | | |
| 메모리 피크(Mi) | | | | |
| Hikari active / pending | | | — | — |
| pg 커넥션 총/활성/대기 (max_connections 대비) | — | — | | — |
| Redis hit ratio | — | — | — | |

## HPA

| 항목 | backend | auth-service |
|---|---|---|
| scale-out 트리거 시각(부하 시작 후) | | |
| 최대 replica | | |
| 안정화 시간(desired 도달 + p95 복귀) | | |

## 판정

| 질문 | 답 |
|---|---|
| 1차 기준(1,000 RPS)에서 p95 목표(<300ms 조회 / <1s 로그인) 충족? | |
| DB 커넥션 고갈 발생? (pending>0 지속 / total≈max_connections) | |
| 병목 1순위 (근거 지표) | |
| 병목 2순위 | |

## 다음 튜닝 권장값 (측정 근거와 함께)

| 노브 | 현재 | 권장 | 근거 |
|---|---|---|---|
| DB_POOL_MAX_SIZE | 20 | | |
| TOMCAT_THREADS_MAX | 200 | | |
| HPA maxReplicas (backend/auth) | 10 / — | | |
| backend/auth resources | 1c·1Gi / 1c·768Mi | | |
| EDU_CACHE_LIST_TTL / COUNTS_TTL | 30s / 60s | | |
| Pooler default_pool_size | 25 / 20 | | |
