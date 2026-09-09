# 로컬(비-K8s: JAR + docker pg/redis) 측정용 캡처 — capture.sh 의 로컬 변형.
# 사용:  powershell -File loadtest/observability/capture-local.ps1 -OutDir loadtest/results/<dir> [-IntervalSec 5]
# 종료:  Ctrl+C
param(
  [Parameter(Mandatory=$true)][string]$OutDir,
  [int]$IntervalSec = 5,
  [string]$BackendProm = "http://localhost:8088/actuator/prometheus",
  [string]$AuthProm = "http://localhost:8089/actuator/prometheus",
  [string]$PgContainer = "lt-db",
  [string]$AuthPgContainer = "lt-auth-db",
  [string]$RedisContainer = "lt-redis",
  [string]$RedisPassword = "ltredis"
)
$ErrorActionPreference = "SilentlyContinue"
New-Item -ItemType Directory -Force $OutDir | Out-Null
$csv = Join-Path $OutDir "local-metrics.csv"
"ts,be_cpu_pct,be_ws_mb,au_cpu_pct,au_ws_mb,be_hik_act,be_hik_pend,be_tomcat_busy,au_hik_act,au_hik_pend,au_tomcat_busy,pg_total,pg_active,pg_wait,aupg_total,redis_hits,redis_misses" |
  Out-File $csv -Encoding utf8

function Get-PidByPort([int]$port) {
  (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1).OwningProcess
}
function Get-Metric([string]$content, [string]$name) {
  $m = [regex]::Match($content, "(?m)^$name(\{[^}]*\})? ([0-9.E+-]+)")
  if ($m.Success) { [double]$m.Groups[2].Value } else { -1 }
}

$bePid = Get-PidByPort 8088; $auPid = Get-PidByPort 8089
$cores = [int]$env:NUMBER_OF_PROCESSORS
$prev = @{}
foreach ($p in @($bePid, $auPid)) { if ($p) { $prev[$p] = (Get-Process -Id $p).TotalProcessorTime.TotalSeconds } }

while ($true) {
  Start-Sleep -Seconds $IntervalSec
  $ts = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
  $cpu = @{}; $ws = @{}
  foreach ($p in @($bePid, $auPid)) {
    if (-not $p) { continue }
    $proc = Get-Process -Id $p
    $now = $proc.TotalProcessorTime.TotalSeconds
    $cpu[$p] = [math]::Round(100 * ($now - $prev[$p]) / $IntervalSec / $cores, 1)
    $prev[$p] = $now
    $ws[$p] = [math]::Round($proc.WorkingSet64 / 1MB, 0)
  }
  $be = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 $BackendProm).Content
  $au = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 $AuthProm).Content
  $pg = docker exec $PgContainer psql -U edumsa -d edumsa -At -F',' -c "SELECT count(*),count(*) FILTER (WHERE state='active'),count(*) FILTER (WHERE wait_event_type='Client' AND state<>'idle') FROM pg_stat_activity WHERE datname='edumsa';" 2>$null
  $aupg = docker exec $AuthPgContainer psql -U eduauth -d eduauth -At -c "SELECT count(*) FROM pg_stat_activity WHERE datname='eduauth';" 2>$null
  $rstats = docker exec $RedisContainer redis-cli -a $RedisPassword --no-auth-warning INFO stats 2>$null
  $hits = ([regex]::Match(($rstats -join "`n"), "keyspace_hits:(\d+)")).Groups[1].Value
  $miss = ([regex]::Match(($rstats -join "`n"), "keyspace_misses:(\d+)")).Groups[1].Value

  "$ts,$($cpu[$bePid]),$($ws[$bePid]),$($cpu[$auPid]),$($ws[$auPid])," +
  "$(Get-Metric $be 'hikaricp_connections_active'),$(Get-Metric $be 'hikaricp_connections_pending'),$(Get-Metric $be 'tomcat_threads_busy_threads')," +
  "$(Get-Metric $au 'hikaricp_connections_active'),$(Get-Metric $au 'hikaricp_connections_pending'),$(Get-Metric $au 'tomcat_threads_busy_threads')," +
  "$pg,$aupg,$hits,$miss" | Out-File $csv -Append -Encoding utf8
}
