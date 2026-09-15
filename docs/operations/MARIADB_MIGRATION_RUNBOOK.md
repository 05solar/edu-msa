# MARIADB_MIGRATION_RUNBOOK.md · PostgreSQL → MariaDB 데이터 이관 + 백업/복원 절차

MariaDB 전환(v0.8.x, [MARIADB_PLAN.md](../planning/MARIADB_PLAN.md)) 이후 운영 절차 문서.
**신규 설치에는 이관이 필요 없다** — Flyway 가 빈 MariaDB 에 스키마를 처음부터 만든다.
이 문서는 ① 기존 PostgreSQL 운영 데이터가 있는 경우의 이관 경로, ② MariaDB 백업/복원
runbook 을 다룬다.

> 현재 리포지토리 기준 확인 결과: 운영 데이터가 있는 PostgreSQL 인스턴스는 저장소/설정
> 어디에도 없다(개발 볼륨뿐). 따라서 이관 절차는 **필요 시점을 위한 runbook** 이며,
> 백업/복원 절차는 compose 환경에서 실측 리허설을 완료했다(§4).

---

## 1. 이관 개요·원칙

- **방식**: 테이블별 CSV 내보내기 → 변환 → `LOAD DATA LOCAL INFILE`.
  `pg_dump` SQL 전체를 MariaDB 에 그대로 넣는 방식은 **금지**(방언 차이로 조용한 손상 위험).
- **스키마는 이관하지 않는다** — 대상 MariaDB 는 반드시 앱(Flyway `db/vendor/mariadb`)이
  먼저 기동해 스키마를 만들게 하고, **데이터만** 옮긴다.
- **순서**: 쓰기 동결 → export → 변환 → import → 검증 → 전환(앱 재기동) → 관찰.
  실패 시 롤백: 동결 이후 쓰기가 없으므로 기존 PostgreSQL 재기동으로 즉시 복귀.

### 타입 변환 규칙

| PostgreSQL | MariaDB | 변환 |
|---|---|---|
| `boolean` (`t`/`f`) | `boolean`(tinyint(1)) | `t`→`1`, `f`→`0` |
| `timestamptz` | `datetime(6)` | **UTC 로 정규화** 후 `YYYY-MM-DD HH:MM:SS.ffffff` (타임존 접미사 제거) |
| `identity` 시퀀스 | `AUTO_INCREMENT` | import 후 `ALTER TABLE t AUTO_INCREMENT = <max(id)+1>` |
| `text`/`varchar` | 동일 | 인코딩 UTF-8 → utf8mb4 (변환 불필요, 검증만) |
| NULL | NULL | CSV 에서 `\N` 표기 유지 |

- 이 프로젝트 스키마에는 UUID/JSON/interval/배열 컬럼이 없다(전 컬럼 표준 타입).
- `deploy_jobs.active_program_id` 는 **STORED 생성 컬럼** — CSV 에 포함하지 말 것(자동 계산).

## 2. 이관 절차

```bash
# ── 0) 사전: 대상 MariaDB 에 앱을 1회 기동해 Flyway 스키마 생성 확인 후 앱 정지
# ── 1) 쓰기 동결 (백엔드/auth 정지 또는 점검 모드)
kubectl -n edu-platform scale deploy/backend deploy/backend-worker deploy/auth-service --replicas=0

# ── 2) PostgreSQL 에서 테이블별 CSV export (UTC 고정)
psql "$PG_URL" -c "SET timezone='UTC';" \
  -c "\copy (SELECT * FROM programs) TO 'programs.csv' WITH (FORMAT csv, HEADER false, NULL '\N')"
#    boolean/timestamptz 변환이 필요한 테이블은 SELECT 절에서 명시 변환:
#      SELECT id, ..., (is_read)::int, to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD HH24:MI:SS.US') ...

# ── 3) MariaDB 로 import (FK 순서: 부모 → 자식. programs → program_* → deployments → ...)
mariadb --local-infile=1 -h <host> -uedumsa -p edumsa -e "
  SET time_zone='+00:00'; SET foreign_key_checks=0;
  LOAD DATA LOCAL INFILE 'programs.csv' INTO TABLE programs
    FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\"' LINES TERMINATED BY '\n';
  SET foreign_key_checks=1;"

# ── 4) AUTO_INCREMENT 재설정 (identity 컬럼 보유 테이블 전부)
mariadb ... -e "SELECT CONCAT('ALTER TABLE ',table_name,' AUTO_INCREMENT=', ...)"  # 표: §2-1
```

**§2-1 identity 테이블 목록** — backend: app_users, programs, comments, notifications,
deployments, deploy_jobs, review_logs / auth: accounts, refresh_tokens.

## 3. 데이터 검증 기준 (이관 후 필수)

테이블별로 아래를 소스/대상 양쪽에서 수집·대조해 기록한다.

| 항목 | 소스(PG) | 대상(MariaDB) | 판정 |
|---|---|---|---|
| row count | `SELECT count(*)` | 동일 쿼리 | 완전 일치 |
| PK min/max | `min(id), max(id)` | 동일 | 완전 일치 |
| NULL count | 컬럼별 `count(*) WHERE c IS NULL` | 동일 | 완전 일치 |
| FK 정합 | 고아 행 0 (`LEFT JOIN ... IS NULL`) | 동일 | 0 건 |
| timestamp 샘플 | 최신 5행 UTC 문자열 | 동일 | 초 단위까지 일치 |
| 한글 샘플 | 이름/설명 5행 | 동일 | 바이트 단위 일치(utf8mb4) |

애플리케이션 레벨 검증: 앱 기동(validate 통과) → 로그인 → 카탈로그 목록/상세 →
알림 목록 → 배포 이력 조회가 이관 데이터로 정상 표시되는지 확인.

## 4. 백업 / 복원 (MariaDB 운영 runbook)

### 자동 백업

`deploy/k8s/platform/mariadb-backup.yaml` — CronJob `edu-db-backup` 이 매일 03:00(UTC)
플랫폼 DB(edumsa)·인증 DB(eduauth)를 `mariadb-dump --single-transaction`(무중단 논리
백업)으로 백업 PVC(`edu-db-backups`)에 gzip 저장, 30일 보존. 수동 즉시 실행:

```bash
kubectl -n edu-platform create job --from=cronjob/edu-db-backup backup-manual-$(date +%s)
kubectl -n edu-platform logs job/backup-manual-<ts> -f
```

### 복원

```bash
# 1) 앱 정지(쓰기 차단)
kubectl -n edu-platform scale deploy/backend deploy/backend-worker deploy/auth-service --replicas=0
# 2) 백업 파일 확인 (백업 PVC 를 임시 파드로 마운트하거나 CronJob 파드 잔존물 활용)
kubectl -n edu-platform run restore --rm -it --image=mariadb:11.4 \
  --overrides='{"spec":{"volumes":[{"name":"b","persistentVolumeClaim":{"claimName":"edu-db-backups"}}],
    "containers":[{"name":"restore","image":"mariadb:11.4","stdin":true,"tty":true,
    "command":["bash"],"volumeMounts":[{"name":"b","mountPath":"/backup"}],
    "env":[{"name":"P","valueFrom":{"secretKeyRef":{"name":"edu-db","key":"MARIADB_ROOT_PASSWORD"}}}]}]}}'
# (파드 안에서)
zcat /backup/edumsa-<ts>.sql.gz | mariadb -h mariadb.edu-platform.svc.cluster.local -uroot -p"$P"
# 3) 검증: 건수/최근 행 확인 → 앱 재기동(replicas 원복) → 헬스/로그인/카탈로그 확인
```

- 덤프는 `--databases` 로 떠서 `CREATE DATABASE`/`USE` 포함 — 빈 인스턴스에도 복원된다.
- **리허설 실측(2026-09-14, compose)**: edumsa 덤프 → DB 삭제 → 복원 → 건수·시드 데이터
  일치 확인 완료. 절차는 위와 동일(호스트만 compose 서비스명).

### 주의

- 백업 PVC 유실 = 백업 유실 — 운영에서는 네트워크 스토리지 클래스 + 오프사이트 복제
  (오브젝트 스토리지 동기화)를 후속 트랙으로 붙인다.
- 데이터가 수십 GiB 이상으로 커지면 논리 백업의 복원 시간이 길어진다 —
  `mariadb-backup`(물리, PITR 가능) 승격을 검토한다.

## 5. 롤백 경로 (전환 직후 장애 시)

1. 앱 정지 → 이미지/매니페스트를 전환 이전 태그로 되돌린다
   (`IMAGE_TAG=git-<이전sha> ./deploy/bootstrap.sh core` — PostgreSQL 매니페스트 포함 커밋).
2. 기존 PostgreSQL 볼륨/인스턴스 재기동(동결 이후 쓰기가 없으므로 정합 유지).
3. DNS/서비스 원복 확인 후 원인 분석. MariaDB 쪽에 쓰기가 발생한 뒤라면 역방향
   이관(§2 의 역순)이 필요하므로, 전환 직후 관찰 기간(권장 1주) 동안 PostgreSQL
   볼륨을 삭제하지 않는다.
