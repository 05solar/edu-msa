# 관측 쿼리 모음 (Prometheus/Grafana — kube-prometheus-stack 기준)

테스트 구간(시작~종료 타임스탬프)을 기록해 두고 아래 쿼리로 판독한다.
capture.sh 의 CSV 와 교차 확인한다.

## 애플리케이션 (backend·auth-service — /actuator/prometheus)

| 지표 | PromQL |
|---|---|
| 요청 p95/p99 (서버 측) | `histogram_quantile(0.95, sum by (le,uri) (rate(http_server_requests_seconds_bucket{application="edu-msa-backend"}[1m])))` |
| 처리량(RPS) | `sum(rate(http_server_requests_seconds_count{application="edu-msa-backend"}[1m]))` |
| 오류율 | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) / sum(rate(http_server_requests_seconds_count[1m]))` |
| **Hikari active** | `hikaricp_connections_active{application=~"edu-msa-backend|edu-auth-service"}` |
| **Hikari pending(대기)** | `hikaricp_connections_pending{...}` — 지속적으로 >0 이면 풀 고갈 신호 |
| Hikari acquire 시간 | `hikaricp_connections_acquire_seconds_max{...}` |
| Tomcat busy threads | `tomcat_threads_busy_threads{...}` |
| JVM heap | `jvm_memory_used_bytes{area="heap",...}` |
| CPU | `rate(process_cpu_usage[1m])` 또는 `container_cpu_usage_seconds_total` |
| rate-limit Redis 폴백 | `edu_auth_ratelimit_failover_total` |
| 배포 임시파일 정리 실패 | `edu_deploy_cleanup_failures_total` |

## MariaDB

- 커넥션 총량/상태: capture.sh `db.csv` (`information_schema.processlist`) —
  `max_connections`(기본 151) 대비 `total_conn` 이 90% 를 넘으면 **커넥션 고갈**로 판정.
- mysqld_exporter(ServiceMonitor) 지표: `mysql_up`,
  `mysql_global_status_threads_connected`, `mysql_global_variables_max_connections`,
  `mysql_global_status_slow_queries` 등 (prometheus-rules 의 EduDb* 경보와 동일 축).
- CPU/IO: `container_cpu_usage_seconds_total{pod=~"mariadb.*|auth-db.*"}`,
  `container_fs_reads_bytes_total` / `container_fs_writes_bytes_total`.

## Redis

- 적중률: capture.sh `redis.csv` (`keyspace_hits/(hits+misses)`) — 카탈로그 캐시 효과 판정.
  30s TTL 기준 정상 부하에서 80%+ 기대. 낮으면 키 분산(필터 조합 과다)·TTL 재검토.

## HPA (scale-out 시점·안정화 시간)

- capture.sh `hpa.csv` 에서 `desired_replicas` 가 처음 증가한 시각(= scale-out 트리거)과
  `current_replicas` 가 desired 에 도달하고 p95 가 이전 수준으로 복귀한 시각(= 안정화)을 판독.
- 보조: `kube_horizontalpodautoscaler_status_desired_replicas{namespace="edu-platform"}`.

## 판정 기준(baseline 기록용 — 튜닝 근거)

| 신호 | 판정 |
|---|---|
| `hikaricp_connections_pending` 지속 > 0 | 앱 풀 부족 → `DB_POOL_MAX_SIZE` 상향 검토(총합 ≤ pg max_connections) |
| pg `total_conn` ≈ max_connections | DB 커넥션 고갈 → 풀러(PgBouncer) 경유 확인·풀 재산정 |
| auth CPU 포화 + login p95 급등 | bcrypt 한계 → auth replica/HPA max 상향 |
| Redis hit < 50% | 캐시 키 폭발 → 검색 파라미터 캐시 제외 검토 |
| HPA maxReplicas 도달 후 p95 유지 실패 | maxReplicas·노드 용량 상향 |
