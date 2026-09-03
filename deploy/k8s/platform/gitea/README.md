# Gitea · 내부 코드 저장소 (1~5단계: 설치 · Ingress/TLS · 파이프라인 · webhook · 예제 미러링)

내부망에서 소스코드가 외부(GitHub)로 나가지 않도록 자체 git 호스팅을 플랫폼 스택에
편입한다. 전체 계획·단계는 [docs/planning/GITEA_PLAN.md](../../../../docs/planning/GITEA_PLAN.md).

## 설치

`bootstrap.sh` 운영스택에 포함되어 있어 보통은 따로 실행할 필요가 없다:

```bash
./deploy/bootstrap.sh stack     # cert-manager·모니터링·…·Gitea 일괄 (best-effort)
```

수동 설치(이 폴더만):

```bash
helm repo add gitea-charts https://dl.gitea.com/charts/
kubectl create ns gitea
kubectl -n gitea create secret generic gitea-admin \
  --from-literal=username=edu-admin --from-literal=password='<강한 비밀번호>'
helm upgrade --install gitea gitea-charts/gitea \
  -n gitea -f deploy/k8s/platform/gitea/values.yaml --wait --timeout 6m
```

## 관리자 계정

- 계정은 `gitea-admin` Secret(키: `username`/`password`)에서 읽는다.
  bootstrap 이 Secret 을 만들 때 `GITEA_ADMIN_USER`/`GITEA_ADMIN_PASSWORD` 환경변수를
  쓰며, 미지정 시 무작위 비밀번호를 생성한다.
- 비밀번호 확인:
  ```bash
  kubectl -n gitea get secret gitea-admin -o jsonpath='{.data.password}' | base64 -d
  ```
- 일반 사용자 셀프 가입은 꺼져 있다(`DISABLE_REGISTRATION: true`) — 1차 운영 방침은
  관리자 발급. SSO 연동은 2차([GITEA_PLAN.md](../../../../docs/planning/GITEA_PLAN.md) §7).

## 접속 (2단계: Ingress)

`bootstrap.sh stack` 이 Ingress 를 함께 배선한다. 호스트는 예제 서비스와 같은 규칙:

| 모드 | 주소 | TLS |
| --- | --- | --- |
| kind | `http://gitea.localhost` | 없음(HTTP) |
| server | `https://gitea.<DOMAIN>` | cert-manager `edu-ca` 자동 발급(`gitea-tls`) |

- `ROOT_URL`/`DOMAIN` 은 bootstrap 이 `--set-string` 으로 모드에 맞게 주입한다
  (웹 링크·clone 안내 URL 이 Ingress 주소와 일치).
- 대용량 push 대비 `nginx.ingress.kubernetes.io/proxy-body-size: 512m` 적용.
- server 모드 clone 시 우리 CA(edu-root-ca)를 신뢰 목록에 추가하거나
  `git -c http.sslCAInfo=<ca.crt> clone …` 을 사용한다.

Ingress 없이 임시 접속(디버깅):

```bash
kubectl -n gitea port-forward svc/gitea-http 3000:3000
# → http://localhost:3000
```

## 하드닝 (2단계)

- 네임스페이스 PodSecurity 라벨: `enforce=baseline`, `warn/audit=restricted` (bootstrap 적용).
- [networkpolicy.yaml](networkpolicy.yaml): 기본 전체 차단 후
  ingress-nginx → 3000 수신과 DNS(53) 송신만 허용. SQLite 내장이라 DB egress 불필요.
  webhook(→ backend) egress 는 4단계에서 별도 정책으로 연다.
- NetworkPolicy 강제: 실서버 Calico는 물론, **최신 kind(kindnet)도 강제한다**
  (4단계 검증 중 egress 차단으로 실증 — 허용 규칙 누락 시 webhook 전송이 막힌다).

## 배포 파이프라인 연동 (3단계)

비공개 레포를 플랫폼이 수집(clone)할 수 있도록 **읽기 전용 봇 + 토큰**을 쓴다.
`bootstrap.sh stack` 이 자동 준비한다(있으면 건너뜀):

- Gitea 계정 `edu-deploy-bot` 생성 + `read:repository` 스코프 토큰 발급
- `edu-platform` 네임스페이스에 Secret `edu-gitea-token`(키: `username`/`token`) 생성
- backend 는 이 Secret 을 env 로 받아 내부 Gitea 레포 clone 시에만 자격 증명을 주입한다

**비공개 레포 공유 방법** — 봇이 읽을 수 있어야 배포된다. 레포에 `edu-deploy-bot` 을
협업자(read)로 초대하거나, 조직 레포는 봇을 조직의 read 팀에 넣는다.

**backend 환경변수**

| 변수 | 의미 | 예 |
| --- | --- | --- |
| `EDU_GITEA_HOST` | 사용자 등록용 공개 호스트(이 호스트의 레포만 자격 증명 주입) | `gitea.edu.internal` |
| `EDU_GITEA_USER` / `EDU_GITEA_TOKEN` | 봇 계정·토큰(Secret 참조) | `edu-deploy-bot` |
| `EDU_GITEA_CLONE_BASE` | clone 실제 접근 주소(내부용, 선택) | `http://gitea-http.gitea.svc:3000` |

`EDU_GITEA_CLONE_BASE` 를 두는 이유 — 사용자는 공개 주소로 등록하지만 수집기는
내부 주소로 받는 분리(split-horizon) 구성이며, **git/curl 이 `*.localhost` 호스트를
RFC 6761 에 따라 무조건 루프백으로 해석**하므로 kind 로컬에서는 필수다.
로컬 compose 개발 시험은:

```bash
kubectl -n gitea port-forward svc/gitea-http 3000:3000 --address 0.0.0.0
EDU_GITEA_HOST=gitea.localhost EDU_GITEA_TOKEN=<봇 토큰> \
EDU_GITEA_CLONE_BASE=http://host.docker.internal:3000 \
  docker compose up -d backend
```

보안: 토큰은 URL·명령 인자에 넣지 않고 **git 환경변수(extraHeader)** 로 주입되어
프로세스 목록·배포 로그·오류 메시지에 노출되지 않는다. Kaniko 빌드는
`edu-gitea-token` Secret 참조 env(GIT_USERNAME/GIT_PASSWORD)로 받는다(매니페스트에 평문 없음).

## push 자동 재배포 (4단계)

레포에 push 하면 등록된 프로그램이 자동으로 재배포된다.

```
push → Gitea webhook(HMAC 서명) → backend /api/webhooks/gitea
     → 서명 검증 → main 브랜치·공개(PUBLIC) 프로그램 매칭 → 배포 큐 적재 → 재배포
```

- **서명 검증** — `edu-gitea-webhook` Secret 의 시크릿으로 `X-Gitea-Signature`(HMAC-SHA256)
  를 상수 시간 비교로 검증한다. 불일치·누락은 401, 시크릿 미설정 시 엔드포인트 자체 비활성(404).
- **대상 제한** — `refs/heads/main` push + 공개 상태 프로그램만. 그 외(브랜치·이벤트·미등록
  레포)는 조용히 무시(200). 배포 레포 주소는 webhook 본문이 아니라 **서버 저장값**만 쓴다(주입 차단).
- **훅 등록** — bootstrap 이 default webhook(관리자 API)을 등록해 **이후 생성되는 레포에
  자동 적용**된다. 기존 레포는 레포 설정 → Webhooks 에서 동일 URL/시크릿으로 추가한다:
  `http://backend.edu-platform.svc:8080/api/webhooks/gitea`
- **주소 매칭** — 등록 주소와 clone_url/html_url 을 정규화(.git·말미 슬래시·대소문자) 비교하므로
  `.git` 유무는 무관하다.

## 예제 미러링 + 시드 주소 전환 (5단계)

예제 7종을 Gitea `edu-examples` 조직으로 미러링한다(재실행 안전 — 레포 재생성 방식):

```bash
./deploy/gitea-seed.sh                       # kind 기본(http://gitea.localhost)
GITEA_URL=https://gitea.edu.internal INSECURE=1 ./deploy/gitea-seed.sh   # 실서버(자체 CA)
```

- 자격은 `GITEA_ADMIN_USER`/`GITEA_ADMIN_PASSWORD` 또는 클러스터 `gitea-admin` Secret 에서 읽는다.
- push 자격 증명은 3단계와 동일하게 extraHeader 환경변수로 주입한다(프로세스 목록 비노출).
- 레포 설명에 한글을 넣지 않는다 — 비 UTF-8 로캘 셸(Windows Git Bash 등)에서 CP949 로
  전송되어 Gitea 가 422 로 거부한다(스크립트 주석 참고).

**시드 프로그램 주소 전환(1차: 수동 절차)** — 시드의 기본 서비스 7종은
`local://examples/<slug>` 주소로 등록돼 있다. Gitea 미러링 후 레포 기반 재배포·webhook
자동 재배포로 전환하려면 플랫폼 DB 의 repo 주소를 바꾼다:

```sql
-- 프로그램 id 1..7 = 예제 7종(seed/programs.json 순서)
UPDATE programs SET repo_url = 'https://gitea.edu.internal/edu-examples/' || slug
 WHERE id BETWEEN 1 AND 7;
```

전환 후에는 각 레포에 배포 봇(`edu-deploy-bot`)을 read 로 초대해야 하며(공개 레포는 불필요),
webhook 은 default webhook 이후 생성 레포엔 자동, 기존 레포엔 개별 등록한다(§4).
2차(시드 로직 옵션화)는 백로그.

## 백업 (필수 운영 절차)

레포 데이터는 PVC `gitea-shared-storage` 에 있다(차트는 Deployment + 단일 PVC).
두 가지 중 하나를 정기 수행:

```bash
# 1) gitea dump (레포+DB+설정 일괄 아카이브) — rootless 이미지라 su 없이 실행
kubectl -n gitea exec deploy/gitea -c gitea -- \
  gitea dump -c /data/gitea/conf/app.ini --file /tmp/gitea-dump.zip
kubectl -n gitea cp "$(kubectl -n gitea get pod -l app=gitea -o jsonpath='{.items[0].metadata.name}')":/tmp/gitea-dump.zip ./gitea-dump.zip -c gitea
# 2) PVC(gitea-shared-storage) 스냅샷 (StorageClass 가 지원할 때)
```

복원 리허설은 6단계 통합 검증 항목이다.

## 운영 DB 전환 (권장, 선택)

1단계는 내장 SQLite 다. 운영 전환 시 플랫폼 CNPG PostgreSQL 에 `gitea` DB 를 만들고
`values.yaml` 의 `gitea.config.database` 를 다음으로 교체 후 `helm upgrade`:

```yaml
database:
  DB_TYPE: postgres
  HOST: edu-db-rw.edu-platform.svc:5432
  NAME: gitea
  USER: gitea
  PASSWD: <secret 참조 권장>
```

## 검증 기록

- 2026-09-01 — kind 에서 1단계 검증: pod 1/1 Running, 관리자 로그인(API 200),
  테스트 레포 생성 + `git clone`/`push` 성공. (검증 절차는 GITEA_PLAN.md §1 완료 기준)
- 2026-09-03 — kind 에서 2단계 검증: helm upgrade 로 ROOT_URL/DOMAIN 반영(app.ini 확인),
  Ingress(gitea.localhost) 경유 웹 200·API 응답, 테스트 레포 생성→`git clone`/`push`
  왕복→삭제 성공. PodSecurity 라벨·NetworkPolicy 3종 적용 확인. (GITEA_PLAN.md §2 완료 기준)
- 2026-09-03 — 3단계 검증: 봇 계정+`read:repository` 토큰 발급, 비공개 레포에 봇
  read 협업자 초대 후 — 무자격 clone 거부 / 봇 토큰(extraHeader 환경변수) clone 성공 /
  backend(compose) E2E 로 비공개 레포 규격 검증 valid=true(토큰)·valid=false(무토큰,
  오류에 토큰 비노출) / Kaniko 템플릿 자격 env 렌더 YAML 유효성 확인. (§3 완료 기준 중
  real 모드 Kaniko 실빌드는 6단계 통합 검증으로 이월)
- 2026-09-03 — 4단계 검증(kind Gitea + compose backend, docker 모드): 비공개 레포
  등록→승인→자동 배포(v1 서빙) 후 push → webhook 전송 → 서명 검증·매칭 → 배포 큐
  적재 → 자동 재배포 → v5 서빙 확인. 음성 4종(서명 오류/누락 401, 비 main 브랜치·
  비 push 이벤트 무시) 통과. 부수 확인: 최신 kind 의 NetworkPolicy 강제(egress 차단
  실증), admin hooks API 는 default webhook(신규 레포 자동 적용)을 만들며 기존 레포는
  개별 등록 필요.
- 2026-09-03 — 6단계 통합 검증(전 구간 인클러스터): bootstrap 클린 배선 → 비공개
  레포 등록→승인→Kaniko(봇 자격)→배포→ingress 200 → push→webhook→재배포 v2 →
  파드 재시작 데이터 유지 → gitea dump 백업·추출 판독. 상세는 GITEA_PLAN.md 진행 기록.
