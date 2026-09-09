#!/usr/bin/env bash
# 부하 테스트 중 리소스·풀·HPA 상태를 주기 수집해 CSV 로 남긴다.
# 사용:  ./loadtest/observability/capture.sh loadtest/results/<날짜>-rps1000  [간격초=5]
# 종료:  Ctrl+C (트랩으로 마무리 기록)
set -euo pipefail
OUT="${1:?결과 디렉터리를 지정하세요}"; INTERVAL="${2:-5}"
NS=edu-platform
mkdir -p "$OUT"

echo "ts,pod,cpu_m,mem_Mi" > "$OUT/pods.csv"
echo "ts,hpa,current_replicas,desired_replicas,cpu_pct" > "$OUT/hpa.csv"
echo "ts,db,total_conn,active_conn,idle_conn,waiting" > "$OUT/pg.csv"
echo "ts,hits,misses,hit_ratio_pct,used_memory_mb" > "$OUT/redis.csv"

# platform-db 접속 정보(개발용 단일 postgres 기준 — HA 는 edu-db-rw 파드로 바꿔 쓴다)
PG_POD="${PG_POD:-deploy/postgres}"
REDIS_POD="${REDIS_POD:-deploy/edu-redis}"
REDIS_PW="$(kubectl -n $NS get secret edu-redis-auth -o jsonpath='{.data.password}' | base64 -d)"

trap 'echo "수집 종료: $OUT"; exit 0' INT TERM

while true; do
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  # 파드 CPU/메모리 (metrics-server)
  kubectl -n $NS top pods --no-headers 2>/dev/null | awk -v ts="$ts" \
    '{gsub("m","",$2); gsub("Mi","",$3); print ts","$1","$2","$3}' >> "$OUT/pods.csv" || true

  # HPA 현재/목표 replica 와 CPU 사용률 — scale-out 시점·안정화 시간 판독용
  kubectl -n $NS get hpa --no-headers 2>/dev/null | awk -v ts="$ts" \
    '{gsub("%","",$3); split($3,a,"/"); print ts","$1","$6","$7","a[1]}' >> "$OUT/hpa.csv" || true

  # PostgreSQL 커넥션 (총/활성/유휴/대기) — 커넥션 고갈 판정용
  kubectl -n $NS exec "$PG_POD" -- psql -U edumsa -d edumsa -At -F',' -c \
    "SELECT '$ts','edumsa',count(*),count(*) FILTER (WHERE state='active'),
            count(*) FILTER (WHERE state='idle'),
            count(*) FILTER (WHERE wait_event_type='Client' AND state<>'idle')
     FROM pg_stat_activity WHERE datname='edumsa';" >> "$OUT/pg.csv" 2>/dev/null || true

  # Redis 적중률·메모리
  kubectl -n $NS exec "$REDIS_POD" -- redis-cli -a "$REDIS_PW" --no-auth-warning INFO stats 2>/dev/null \
    | awk -v ts="$ts" -F':' '
      /keyspace_hits/{h=$2} /keyspace_misses/{m=$2}
      END{ if(h+m>0) r=100*h/(h+m); else r=0; printf "%s,%d,%d,%.1f,", ts, h, m, r }' >> "$OUT/redis.csv" || true
  kubectl -n $NS exec "$REDIS_POD" -- redis-cli -a "$REDIS_PW" --no-auth-warning INFO memory 2>/dev/null \
    | awk -F':' '/used_memory:/{printf "%.1f\n", $2/1048576; exit}' >> "$OUT/redis.csv" || true

  sleep "$INTERVAL"
done
