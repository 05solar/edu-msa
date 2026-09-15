# loadtest · 부하 테스트 체계 (k6)

용량 산정을 위한 **측정 환경**이다. 목표 기준: 총 20만~50만 계정, 피크 동접 1만~5만,
1차 기준 시나리오 = **동접 1만 · 약 1,000 RPS**. 이번 단계는 baseline 측정이 목적이며
임의 튜닝을 하지 않는다.

> **금지**: 운영 DB·실사용자 데이터에 절대 실행하지 않는다. 전용 부하 테스트 환경
> (kind 리허설 또는 스테이징 클러스터, `EDU_DEPLOY_MODE=simulate`)에서만 수행한다.
> 시드/정리 스크립트는 `lt_`/`lt-prog-` 접두어만 다루므로 실수로 섞여도 식별·제거 가능하다.

## 도구: k6

선택 이유 — RPS 기반 arrival-rate 실행기(목표 처리량을 직접 지정), JS 시나리오(프론트 팀 스택과
동일), 단일 바이너리(폐쇄망 반입 용이), p50/p95/p99·오류율 요약 내장.

```bash
# 설치: https://k6.io/docs/get-started/installation (단일 바이너리)
```

## 1. 준비

```bash
# ① 대상 환경 기동 (예: kind 리허설)
./deploy/bootstrap.sh up

# ② 테스트 데이터 시드 — 계정 20만(목표 상단은 50만), 프로그램 2천
#    먼저 템플릿 계정 1개를 회원가입(비밀번호 LoadTest#2026! — bcrypt 해시 복제용):
curl -X POST $AUTH/api/auth/signup -H 'Content-Type: application/json' -d '{
  "username":"lt_template","password":"LoadTest#2026!","name":"부하템플릿",
  "email":"lt_template@loadtest.local","dept":"부하테스트"}'
( echo "SET @count=200000;"; cat loadtest/seed/seed-accounts.sql ) | mariadb -h <auth-db 호스트> -ueduauth -p eduauth
( echo "SET @count=2000;";   cat loadtest/seed/seed-programs.sql ) | mariadb -h <db 호스트>      -uedumsa  -p edumsa

# ③ 스모크(배선 확인)
k6 run -e BASE_URL=http://edu.localhost loadtest/k6/smoke.js
```

## 2. 실행 (단계적 load profile)

각 단계는 **관측 수집과 함께** 실행한다. 결과 디렉터리 규칙: `loadtest/results/<YYYYMMDD-HHMM>-<프로파일>/`

```bash
RESULT=loadtest/results/$(date +%Y%m%d-%H%M)-rps1000
mkdir -p "$RESULT"

# ① 관측 수집(백그라운드) — 파드 CPU/메모리·HPA·DB 커넥션·redis 적중률 CSV
./loadtest/observability/capture.sh "$RESULT" 5 &

# ② 혼합 시나리오 — PROFILE 을 rps100 → rps500 → rps1000 → rps2000 순으로 단계 실행
k6 run -e PROFILE=rps1000 -e BASE_URL=http://edu.localhost -e ACCOUNTS=200000 \
  --summary-export "$RESULT/summary.json" loadtest/k6/mixed.js | tee "$RESULT/k6.log"

kill %1   # 수집 종료
```

로그인 폭주(별도):
```bash
k6 run -e BURST_RPS=200 -e BASE_URL=http://edu.localhost -e ACCOUNTS=200000 \
  --summary-export "$RESULT/login-burst.json" loadtest/k6/login-burst.js
```

배포 요청(저빈도 — simulate 모드 필수):
```bash
k6 run -e ADMIN_USER=<관리자> -e ADMIN_PASS=<비밀번호> loadtest/k6/deploy-flow.js
```

## 3. 시나리오 구성 (mixed.js — 프론트 실사용 패턴 비중)

| exec | 경로 | 비중 |
|---|---|---|
| catalogList | GET /api/programs (페이지·정렬) | 35% |
| catalogSearch | GET /api/programs (cat·q 필터) | 20% |
| programDetail | GET /api/programs/{id} | 20% |
| catalogCounts | GET /api/programs/counts | 10% |
| notifications | GET /api/notifications + unread-count | 10% |
| tokenRefresh | POST /api/auth/refresh (쿠키 회전) | 4% |
| steadyLogin | POST /api/auth/login | 1% |

## 4. 결과 저장 형식

- `summary.json` — k6 `--summary-export` (p50/p95/p99·오류율·RPS, 시나리오/태그별)
- `k6.log` — 실행 로그(마지막 줄에 한 줄 요약)
- `pods.csv` / `hpa.csv` / `db.csv` / `redis.csv` — capture.sh 수집분
- 판독 후 `loadtest/RESULTS.template.md` 를 복사해 `RESULTS-<날짜>.md` 로 기록
- Prometheus 판독 쿼리: `loadtest/observability/queries.md`

## 5. 정리

```bash
# --force: cleanup.sql 은 platform/auth 두 DB 의 문장을 모두 담고 있어
# 상대 DB 테이블이 없다는 오류를 건너뛰고 계속 진행해야 한다.
mariadb --force -h <db 호스트>      -uedumsa  -p edumsa  < loadtest/seed/cleanup.sql
mariadb --force -h <auth-db 호스트> -ueduauth -p eduauth < loadtest/seed/cleanup.sql
```

## 주의

- 429 는 로그인 방어 정책의 정상 동작이다 — login-burst 에서 `login_rate_limited` 카운터로
  따로 집계되며 오류율에 넣지 않는다.
- 부하 생성기는 대상 클러스터 **밖**(별도 머신)에서 실행해야 생성기 CPU 가 결과를 오염시키지 않는다.
- rps2000 이상은 생성기 한 대의 한계(파일 디스크립터·CPU)를 먼저 확인한다
  (`ulimit -n 65536`, k6 는 코어당 ~2500 VU 수준).
