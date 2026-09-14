# PRODUCTION.md · 실서버(멀티 노드) 배포 가이드

로컬 kind 데모를 실제 운영 클러스터로 옮기는 전체 절차. 요약은 **"멀티 노드 클러스터를 만들고,
이미지 레지스트리·네트워크 스토리지를 정하고, L4 LB·도메인·TLS 붙이고, `bootstrap.sh` 를
server 모드로 돌린다"** 이다.

> 전제: **운영은 단일 노드를 지원하지 않는다.** 단일 서버(k3s 1대) 구성은 로컬 리허설·데모
> 전용이며(§9), 운영 토폴로지는 **워커 3대 이상 + HA 컨트롤플레인(또는 매니지드 K8s)** 이다.
> 플랫폼 매니페스트(backend/auth/frontend/테넌트 템플릿)는 replica 를 노드/존에 분산(soft)하도록
> 작성되어 있어, 노드가 여러 개면 자동으로 퍼지고 노드 1대 장애 시에도 서비스가 유지된다.

> 중요: 이 플랫폼 **코어는 GPU가 필요 없다(CPU 전용)**. GPU는 배포되는 **테넌트 서비스**가
> `service.yaml` 에서 `resources.gpu>=1` 로 요청할 때만 쓰인다 → [k8s/platform/gpu/README.md](k8s/platform/gpu/README.md).
> GPU 서버는 클러스터에 **워커로 join** 시키고 taint 로 일반 워크로드를 격리한다.

---

## 0. 한눈에 (원커맨드까지 5단계)

```bash
# ① 클러스터 — 매니지드(권장) 또는 k3s HA(서버 3 + 워커 N) → §1
# ② 네트워크 스토리지 StorageClass 준비(노드 종속 local-path 금지) → §1-1
# ③ (테넌트 GPU 쓸 때만) GPU Operator
WITH_GPU=1 ./deploy/bootstrap.sh gpu

# ④ 코어 + 운영스택 한 번에 (STORAGE_CLASS 로 PVC 스토리지 클래스 주입)
MODE=server DOMAIN=edu.example.go.kr REGISTRY=<레지스트리 접두어> \
  STORAGE_CLASS=<네트워크 스토리지 클래스> WITH_STACK=1 ./deploy/bootstrap.sh up

# ⑤ DNS: edu.example.go.kr → L4 LB(ingress-nginx Service EXTERNAL-IP)
kubectl -n ingress-nginx get svc ingress-nginx-controller
```

kind(로컬)과 **똑같은 스크립트**다. 차이는 `MODE=server`, 실제 `DOMAIN`·`REGISTRY`·`STORAGE_CLASS` 뿐.
server 모드는 노드가 3개 미만이거나 `STORAGE_CLASS` 미지정이면 경고를 출력한다.

---

## 1. 클러스터 준비 (멀티 노드)

두 경로 중 하나를 택한다. **어느 쪽이든 워커(에이전트) 3대 이상**이 운영 최소 구성이다.

### A. 매니지드 Kubernetes (권장 — GKE/EKS/AKS/NKS 등)
- 컨트롤플레인 HA·업그레이드를 공급자가 관리한다. 노드풀을 워커 3대 이상으로 만들고
  그 kubeconfig 로 이후 절차를 그대로 수행하면 된다.
- L4 LB(§3)·StorageClass(§1-1)가 기본 제공되므로 이름만 확인해 넘기면 된다.

### B. 자체 구축 — k3s HA (컨트롤플레인 3 + 워커 N)
```bash
# 서버(컨트롤플레인) 1번 — embedded etcd 클러스터 시작
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="server --cluster-init \
  --disable traefik --flannel-backend=none --disable-network-policy" sh -

# 서버 2·3번 — 1번에 join (K3S_TOKEN 은 1번의 /var/lib/rancher/k3s/server/node-token)
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="server --server https://<서버1>:6443 \
  --disable traefik --flannel-backend=none --disable-network-policy" K3S_TOKEN=<token> sh -

# 워커(에이전트) 3대 이상 — GPU 박스도 여기로 join (이후 taint 로 격리)
curl -sfL https://get.k3s.io | K3S_URL=https://<서버1>:6443 K3S_TOKEN=<token> sh -

export KUBECONFIG=/etc/rancher/k3s/k3s.yaml            # 또는 ~/.kube/config 로 복사
kubectl get nodes                                      # 서버 3 + 워커 N 확인
```
- 컨트롤플레인 API 앞에는 고정 진입점(L4 LB 또는 VIP/keepalived)을 두고
  join 주소·kubeconfig 가 그 주소를 보게 한다(서버 1대 장애 시에도 API 유지).
- `--flannel-backend=none --disable-network-policy` 로 기본 CNI를 끄고 **Calico** 를 설치한다
  (NetworkPolicy 실제 강제; 신뢰등급 격리에 필수).
  ```bash
  kubectl create -f https://raw.githubusercontent.com/projectcalico/calico/v3.28.0/manifests/tigera-operator.yaml
  kubectl create -f https://raw.githubusercontent.com/projectcalico/calico/v3.28.0/manifests/custom-resources.yaml
  ```

### 1-1. 스토리지 (노드 종속 금지)

k3s 기본 `local-path` 는 **볼륨이 특정 노드에 묶여** 그 노드가 죽으면 DB 파드가 재스케줄되지
못한다 — **운영에서 사용 금지.** 노드 장애 시에도 다른 노드에서 붙을 수 있는 스토리지를 쓴다.

| 환경 | StorageClass 예 |
|---|---|
| 매니지드 | 기본 제공 CSI (`gp3`/`pd-balanced`/`managed-csi` 등) |
| 자체 구축 | Longhorn(간단, replica 스토리지) · Rook-Ceph · 외부 NFS/SAN CSI |

준비한 클래스 이름을 `STORAGE_CLASS` 로 넘기면 bootstrap 이 코어 PVC(mariadb·auth-db·백업)와
운영스택(Loki·Gitea) 볼륨에 일괄 적용한다. 미지정 시 클러스터 기본 클래스를 쓴다.
DB 백업·HA 트랙은 §4에서 별도로 다룬다.

---

## 2. 이미지 레지스트리

`REGISTRY` 는 노드가 pull 할 수 있는 이미지 접두어다. 택1:
- **사내 레지스트리**: `REGISTRY=registry.example.go.kr:5000` (인증 필요 시 노드에 pull secret).
- **k3s 임베디드/로컬**: 간단히는 `docker save | k3s ctr images import` 로 노드에 직접 적재.
- **매니지드**: GAR/ECR 등. `bootstrap.sh images` 가 `REGISTRY/edu-msa-*` 로 push 한다.

`bootstrap.sh up` 이 `images` 단계에서 backend·auth-service·frontend 3종을 빌드·push 한다.
(빌드 호스트에 docker 필요. 클러스터 노드와 빌드 호스트가 다르면 REGISTRY 는 양쪽에서 도달 가능해야 함.)

---

## 3. 도메인 · TLS · L4 로드밸런서

- 트래픽 경로는 **클라이언트 → L4 LB → ingress-nginx(2 replica, 노드 분산) → 서비스** 를 전제로 한다.
  - 매니지드: ingress-nginx Service `type: LoadBalancer` 가 클라우드 L4 LB 를 자동 프로비저닝.
  - 자체 구축: MetalLB(또는 하드웨어 L4)로 `LoadBalancer` 를 구현하거나, NodePort + 외부 L4 를 쓴다.
  - 클라이언트 IP 보존: `externalTrafficPolicy: Local`(edge/ingress-nginx-values.yaml 기본) + LB 헬스체크,
    또는 proxy-protocol. 클라이언트 IP 는 인증 rate-limit(계정/IP 기준)에 쓰이므로 반드시 보존한다.
- `DOMAIN` 의 A레코드를 L4 LB 주소로:
  ```bash
  kubectl -n ingress-nginx get svc ingress-nginx-controller   # EXTERNAL-IP 확인
  ```
- TLS: 운영스택이 **cert-manager** 를 깔고 `ClusterIssuer` 를 만든다. 사내망은 내부 CA(`edu-ca`),
  공인 도메인은 `clusterissuers.yaml` 의 `edu-ca` 를 **Let's Encrypt(ACME)** 발급자로 교체하면
  Ingress 주석만으로 공인 인증서가 자동 발급된다. (edge/cert-manager/README.md)

---

## 4. 데이터베이스 (MariaDB — 단일 인스턴스 + 백업, HA 는 후속 트랙)

`bootstrap.sh` 코어가 **MariaDB 11.4 단일 인스턴스 2개**(플랫폼 `mariadb`/edumsa,
인증 `auth-db`/eduauth — utf8mb4·UTC·mysqld_exporter 사이드카)와 **일일 백업 CronJob** 을
함께 배포한다. 1차 운영 구성은 "단일 인스턴스 + 네트워크 스토리지 + 일일 백업 + 복원
리허설"이며, 다중화(HA)는 후속 트랙이다(§4-3).

### 4-1. 백업·복원

- `platform/mariadb-backup.yaml` — CronJob `edu-db-backup` 이 매일 03:00(UTC) 두 DB 를
  `mariadb-dump --single-transaction`(무중단 논리 백업)으로 백업 PVC(`edu-db-backups`)에
  gzip 저장, 30일 보존. `STORAGE_CLASS` 를 백업 PVC 에도 반드시 네트워크 스토리지로 지정할 것.
- 수동 즉시 백업 / 복원 절차·리허설:
  **[docs/operations/MARIADB_MIGRATION_RUNBOOK.md](../docs/operations/MARIADB_MIGRATION_RUNBOOK.md)** §4.
  복구 리허설(백업에서 실제로 복원되는지)을 분기 1회 이상 수행할 것.
- 오브젝트 스토리지 오프사이트 복제(기존 `edu-db-backup-creds` 활용)는 후속 트랙 —
  백업 PVC 유실이 곧 백업 유실이므로 운영 개시 전 도입을 권장한다.

### 4-2. 기존 데이터 마이그레이션 (PostgreSQL → MariaDB)

전환 이전(PostgreSQL) 운영 데이터가 있는 경우의 테이블별 CSV 이관 절차·검증 기준·롤백
경로는 **[docs/operations/MARIADB_MIGRATION_RUNBOOK.md](../docs/operations/MARIADB_MIGRATION_RUNBOOK.md)**
§1~§3 을 따른다. 신규 설치는 이관이 필요 없다(Flyway 가 빈 DB 에 스키마 생성).

### 4-3. HA 후속 트랙 (규모 확장 시)

수십만 사용자·수만 동시접속 규모로 가면 다음 순서로 승격한다(코드 준비는 되어 있다 —
`DB_RO_URL` 미설정 시 read 라우팅이 꺼지는 현행 구조 유지):

1. **복제 구성**: mariadb-operator(비동기 replication 또는 Galera) 로 primary 1 + replica N.
2. **프록시/풀러**: MaxScale(read-write split) 또는 ProxySQL — 앱 커넥션 총량 상한 관리.
3. **read 라우팅 재배선**: backend `DB_RO_URL` 을 replica(또는 MaxScale ro 리스너)로 지정 —
   카탈로그 목록/집계(캐시 미스분)가 replica 로 분산된다(ReadRoutingDataSourceConfig).
4. **백업 승격**: 논리(mariadb-dump) → 물리(mariabackup) + binlog 보존으로 PITR 확보.

주의: MariaDB 10.6 미만은 지원하지 않는다(`FOR UPDATE SKIP LOCKED` — 배포 큐 선점 경로).

---

## 5. 시크릿 (실서버 필수 — Sealed Secrets)

매니페스트에는 **자리표시자 Secret 이 없다.** 운영 반영 경로는 하나뿐이다:

1. [deploy/k8s/secrets/README.md](k8s/secrets/README.md) 절차대로 **Sealed Secrets** 로
   `edu-db` · `edu-auth-db` · `edu-auth-jwt` · `edu-redis-auth` · `edu-gitea-db`
   (오프사이트 백업 복제 도입 시 `edu-db-backup-creds`)를
   먼저 반영한다. 평문 Secret 파일은 `.gitignore` 로 커밋이 차단되고, 봉인본(`*.sealed.yaml`)만 커밋한다.
2. `bootstrap.sh` server 모드는 필수 Secret 이 없으면 **안내와 함께 중단**한다(fail-closed) —
   자리표시자 값이 운영에 올라갈 경로가 존재하지 않는다.
3. 로컬/리허설(kind)은 bootstrap 이 무작위 값으로 자동 생성하고,
   compose 개발 환경은 `deploy/.env`(예시 `.env.example`)로 주입한다.

`EDU_JWT_SECRET` 은 auth-service(발급)와 backend(검증)가 **같은 값**을 봐야 한다(둘 다 `edu-auth-jwt`).

## 5-1. 이미지 버전 정책 (불변 태그 · 롤백)

- **`:latest` 를 쓰지 않는다.** 태그 정책:
  - CI(`.github/workflows/release.yml`): main push/릴리스 태그마다 **`git-<short sha>`** 로
    빌드·push, `v*` 태그 릴리스에는 **semver 태그**를 추가 부여 → GHCR.
  - 매니페스트는 고정 semver 를 기본값으로 담고, `bootstrap.sh` 가 `IMAGE_TAG`
    (기본 `git-<현재 커밋 sha>`)로 치환해 적용한다. `imagePullPolicy: IfNotPresent`
    (불변 태그 전제라 재-pull 불필요, 롤백 시 노드 캐시 활용).
- **롤백**: 두 경로 —
  ```bash
  # ① 이전 태그로 재배포(감사 추적 명확)
  IMAGE_TAG=git-<이전sha> MODE=server DOMAIN=... REGISTRY=... ./deploy/bootstrap.sh core
  # ② 직전 리비전 즉시 복귀
  kubectl -n edu-platform rollout undo deploy/backend   # auth-service·frontend 동일
  ```
- CI(GHCR) 이미지를 내부망 레지스트리로 미러링해 쓰거나, 폐쇄망이면 내부 레지스트리에
  같은 태그 정책으로 직접 push 한다(`bootstrap.sh images` 는 IMAGE_TAG 로 push).

---

## 6. 운영스택 (WITH_STACK=1 이 자동 설치)

`bootstrap.sh up` 이 helm으로 설치 시도(각 단계 best-effort): cert-manager · kube-prometheus-stack
(Prometheus·Grafana·Alertmanager) · KEDA(scale-to-zero) · Loki(로그) · Tempo(트레이스).
개별 상태·설정은 각 폴더 README 참고. WAF(ModSecurity/OWASP CRS)는
`edge/ingress-nginx-values.yaml` 로 활성화한다(edge/README.md).

코어만 빠르게 확인하려면 `WITH_STACK=0 ... ./deploy/bootstrap.sh up`.

---

## 7. GPU (테넌트 서비스용)

```bash
WITH_GPU=1 ./deploy/bootstrap.sh gpu     # NVIDIA GPU Operator
```
이후 테넌트가 `service.yaml` 에 `resources.gpu: 1` 을 넣으면 배포 매니페스트 limits 에
`nvidia.com/gpu` 가 자동 추가된다. taint·격리 주의는 [k8s/platform/gpu/README.md](k8s/platform/gpu/README.md).

---

## 8. 확인 · 롤백

```bash
./deploy/bootstrap.sh status                 # 파드/서비스/인그레스 + 접속 URL
kubectl get pods -A                          # 전체
./deploy/bootstrap.sh down                   # 코어 제거(운영스택 helm 은 개별 uninstall)
```

배포 파이프라인(테넌트 등록→승인→기동)·모드(simulate/docker/real) 세부는 [INFRA.md](INFRA.md) §3,
백엔드 API/RBAC 는 [../backend/README.md](../backend/README.md).

---

## 9. 로컬·데모 전용: 단일 노드 구성 (운영 아님)

리허설·시연은 단일 노드로 충분하며 **운영 토폴로지 요건(§1)을 적용하지 않는다.**

- **kind (권장 로컬)**: `./deploy/bootstrap.sh up` — 별도 설정 없이 전체 스택 리허설.
- **k3s 1대**: `curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik" sh -` 후
  `MODE=server ... bootstrap.sh up` — bootstrap 이 "노드 3개 미만" 경고를 출력하지만 동작은 한다
  (분산 제약이 전부 soft 라 단일 노드에도 스케줄된다). `local-path` 스토리지도 로컬에선 허용.
- 단일 노드 구성은 노드 장애 = 전면 중단이며, 수십만 사용자 운영 기준을 충족하지 않는다.
