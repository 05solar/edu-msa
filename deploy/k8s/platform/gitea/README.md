# Gitea · 내부 코드 저장소 (1단계: 설치)

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

## 접속 (1단계: 포트포워딩)

Ingress/TLS 는 2단계에서 배선한다. 그 전에는:

```bash
kubectl -n gitea port-forward svc/gitea-http 3000:3000
# → http://localhost:3000
```

## 백업 (필수 운영 절차)

레포 데이터는 PVC(`data-gitea-0` 형태)에 있다. 두 가지 중 하나를 정기 수행:

```bash
# 1) gitea dump (레포+DB+설정 일괄 아카이브)
kubectl -n gitea exec deploy/gitea -- su git -c "gitea dump -c /data/gitea/conf/app.ini"
# 2) PVC 스냅샷 (StorageClass 가 지원할 때)
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
