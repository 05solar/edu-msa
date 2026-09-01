# DEPLOY.md · 원커맨드 배포 & GPU 서버 안내

edu-msa 를 **명령어 한 번으로** Kubernetes 에 올리는 방법과, **실제 GPU 서버**에서 돌리는 방법을
한곳에 정리한다. 세부는 [deploy/PRODUCTION.md](../../deploy/PRODUCTION.md) · [deploy/INFRA.md](../../deploy/INFRA.md).

> 먼저 알아둘 점 — **플랫폼 코어(React 프론트 + Spring Boot backend/auth + PostgreSQL)는 GPU가
> 필요 없다. CPU만으로 동작한다.** GPU는 **배포되는 테넌트 서비스**가 `service.yaml` 에서
> `resources.gpu >= 1` 로 요청할 때만 쓰인다.

---

## 1. 원커맨드 (kind 로컬 / 실서버 겸용)

`deploy/bootstrap.sh` 하나로 **클러스터 준비 → 이미지 3종 빌드/푸시 → 코어 배포 →
(선택)운영스택 → (선택)GPU** 까지 끝낸다. `Makefile` 은 그 얇은 래퍼다.

```bash
# 로컬 데모 (kind 자동 생성, 코어+운영스택 전체)
./deploy/bootstrap.sh up            # 또는:  make up
# 접속: http://edu.localhost  (브라우저가 *.localhost 를 127.0.0.1 로 해석)

# 코어만 빠르게
WITH_STACK=0 ./deploy/bootstrap.sh up      # 또는:  make core

# 실서버(기존/신규 클러스터)
MODE=server DOMAIN=edu.example.go.kr REGISTRY=<레지스트리> ./deploy/bootstrap.sh up

# 테넌트가 GPU 를 쓰면 GPU Operator 추가
WITH_GPU=1 ./deploy/bootstrap.sh gpu

# 서브 프로그램(예제 7종)까지 처음부터 함께
WITH_EXAMPLES=1 ./deploy/bootstrap.sh up
```

### 서브커맨드 / 환경변수

| 서브커맨드 | 하는 일 |
|---|---|
| `up` (기본) | 준비 → 빌드/푸시 → 코어 → (WITH_STACK)운영스택 → (WITH_GPU)GPU |
| `core` | 코어만(클러스터+이미지+코어 배포) |
| `images` | 이미지 3종 빌드/푸시만 |
| `stack` | 운영스택(helm)만 |
| `gpu` | NVIDIA GPU Operator만 |
| `examples` | 서브 프로그램 7종(examples/) 배포 — [§1-1](#1-1-서브-프로그램예제-7종-배포) |
| `status` / `down` | 상태 확인 / 제거 |

| env | 기본값 | 설명 |
|---|---|---|
| `MODE` | `kind` | `kind`(자동 생성) 또는 `server`(현재 kubeconfig) |
| `DOMAIN` | kind `edu.localhost` / server `edu.internal` | 플랫폼 접속 도메인 |
| `REGISTRY` | kind `localhost:5001` / server `registry.edu.internal` | 이미지 접두어 |
| `WITH_STACK` | `up` 에서 `1` | 모니터링·WAF·KEDA·로그·트레이스·cert-manager |
| `WITH_GPU` | `0` | NVIDIA GPU Operator 설치 |
| `WITH_EXAMPLES` | `0` | `up` 시 서브 프로그램 7종도 함께 배포 |
| `IMAGE_TAG` | `latest` | 이미지 태그 |

### Production 타겟 (edu-poc.headit.kr 운영 서버)

운영 도메인이 고정된 전용 타겟이 있다. `IMAGE_TAG` 가 실행 시각으로 자동 부여되어
재배포 때마다 롤링 업데이트가 일어난다.

```bash
make prod-preflight PROD_REGISTRY=<레지스트리>  # ★ 먼저 — 전제조건 일괄 점검(읽기 전용)
make prod-deploy    PROD_REGISTRY=<레지스트리>  # 최초/전체 (코어+운영스택)
make prod-core      PROD_REGISTRY=<레지스트리>  # 코드 반영 재배포 (평시 사용)
make prod-examples  PROD_REGISTRY=<레지스트리>  # 기본 예제 7종 (와일드카드 DNS 필요)
make prod-registry-secret PROD_REGISTRY=.. REG_USER=.. REG_PASS=..  # Kaniko push 인증(사설 레지스트리)
make prod-status                                # 상태 확인
```

**GitHub 레포 빌드(real 파이프라인)가 실서버에서 동작하기 위한 전제** — `prod-preflight` 가 점검한다:

1. **RBAC** — `rbac.yaml` 이 배포 SA(edu-deployer)에 Kaniko Job(batch)·HPA·PDB 권한까지
   부여한다(코어 배포에 포함). 이 권한이 없으면 배포가 전부 Forbidden 으로 실패한다.
2. **레지스트리** — `PROD_REGISTRY` 는 ①운영자 PC(docker push) ②클러스터 안 Kaniko(push)
   ③노드 kubelet(pull) 세 곳 모두에서 같은 주소로 접근 가능해야 한다.
   사설이면 `make prod-registry-secret` 으로 push 인증(edu-registry-auth)을 만들고,
   노드 pull 인증은 노드 containerd 설정 또는 imagePullSecrets 로 별도 구성한다.
   HTTP(비TLS) 레지스트리면 backend env `EDU_DEPLOY_KANIKO_INSECURE=true`.
3. **egress** — 클러스터에서 github.com(clone)·gcr.io(Kaniko 이미지) 접근 필요.
   폐쇄망이면 Kaniko executor 이미지를 사내 레지스트리로 미러링.

**예제 7종의 서브도메인**(`<slug>.edu-poc.headit.kr`)은 ① DNS `*.edu-poc.headit.kr` →
서버 IP, ② 전면 Nginx 에 `server_name *.edu-poc.headit.kr` 프록시 블록
(`proxy_set_header Host $host` 필수), ③ 와일드카드 인증서(LE DNS-01) 가 전제다.
등록 프로그램의 real 파이프라인은 경로 방식(`/svc/<slug>`)이라 이 전제 없이 동작한다.
(전면 Nginx 가 TLS 종료 후 ingress 로 HTTP 프록시하면 ingress 의 ssl-redirect 로
308 루프가 날 수 있다 — ingress-nginx 설정 `ssl-redirect: "false"` 또는 HTTPS 프록시
+`proxy_ssl_verify off` 로 해소.)

### 사전 준비물

- 공통: `docker`, `kubectl`, `git` (실행 중인 docker 데몬)
- kind 모드: `kind`
- 운영스택/GPU: `helm`

> 운영스택 단계는 helm 설치라 클러스터·네트워크 상황에 따라 개별 실패할 수 있어 **각 단계
> best-effort**(실패해도 다음 진행 + 경고)로 동작한다. 개별 상태는 `kubectl get pods -A` 로 확인.

### 1-1. 서브 프로그램(예제 7종) 배포

`examples/` 의 기본 내장 서비스 7개(맞춤법 검사기·자리배치 생성기 등)는 코어와 별도로 배포한다.
시드 프로그램의 레포 주소가 `local://examples/<slug>` 라서 K8s(real) 모드의 Kaniko 파이프라인
(git 레포 전용)으로는 자동 배포되지 않기 때문이다.

```bash
# 코어가 떠 있는 상태에서 한 번에 (빌드→푸시→배포→DB 등록)
./deploy/bootstrap.sh examples          # 또는:  make examples

# 처음부터 코어와 함께
WITH_EXAMPLES=1 ./deploy/bootstrap.sh up
```

하는 일과 접속 방식:

- 이미지 7종을 빌드해 레지스트리에 푸시 (`<REGISTRY>/edu-svc-<slug>:<IMAGE_TAG>`)
- `edu-services` 네임스페이스에 Deployment(비루트 securityContext)/Service/Ingress 적용
- 접속은 **서브도메인 라우팅**: kind `http://<slug>.localhost` (브라우저가 `*.localhost` 를
  127.0.0.1 로 해석) / server `https://<slug>.<DOMAIN>` (**와일드카드 DNS** `*.<DOMAIN>` →
  ingress IP 필요, TLS 는 cert-manager `edu-ca` 발급)
- 플랫폼 DB(`deployments` 테이블)에 RUNNING 레코드를 등록해 프론트 프로그램 상세의
  **"웹에서 바로 사용"** 버튼이 실제 주소로 연결되게 한다 (재실행 안전 — 기존 레코드 교체)

> 서비스 이미지는 Dockerfile 의 `USER` 가 **숫자 UID** 여야 한다(예: `USER 1000:1000`).
> 이름 기반(`USER node`)이면 K8s `runAsNonRoot` 검증이 비루트임을 확인하지 못해
> `CreateContainerConfigError` 로 기동에 실패한다.

---

## 2. 실제 GPU 서버에서 작동시키기

"GPU 서버에서 작동"은 두 갈래다.

### (가) 플랫폼을 GPU 서버의 k8s 에 올리기 — GPU 미사용

서버에 k8s 가 없으면 **k3s** 가 가장 간단하다.

```bash
# ① 클러스터 (내장 traefik 끔 — 우리는 ingress-nginx 사용)
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik" sh -
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

# ② 코어 + 운영스택 한 번에
MODE=server DOMAIN=edu.example.go.kr REGISTRY=<레지스트리> ./deploy/bootstrap.sh up

# ③ DNS: edu.example.go.kr → ingress 외부 IP
kubectl -n ingress-nginx get svc ingress-nginx-controller
```
NetworkPolicy 강제(신뢰등급 격리)가 필요하면 k3s 기본 CNI 대신 **Calico** 를 쓴다 —
플래그·명령은 [deploy/PRODUCTION.md §1](../../deploy/PRODUCTION.md).

### (나) 테넌트 서비스가 GPU 를 쓰게 하기

```bash
WITH_GPU=1 ./deploy/bootstrap.sh gpu     # NVIDIA GPU Operator (device-plugin 포함)
```
이후 서비스 레포의 `service.yaml` 에 GPU 개수를 지정하면 배포 매니페스트에 자동 반영된다.

```yaml
# service.yaml
resources:
  cpu: "1"
  memory: "2Gi"
  gpu: 1          # 0=미사용(기본). 1~8 이면 limits 에 nvidia.com/gpu 자동 추가
```
GPU 노드 taint·격리(공개 tier·MIG·runtimeClass) 주의는
[deploy/k8s/platform/gpu/README.md](../../deploy/k8s/platform/gpu/README.md).

### 실서버 체크리스트 (kind 와 다른 부분)

| 항목 | kind(로컬) | 실서버 |
|---|---|---|
| 클러스터 | 스크립트가 자동 생성 | k3s/kubeadm/매니지드 |
| CNI | kindnet(NP 미강제) | **Calico**(NetworkPolicy 강제) |
| 스토리지(PVC) | 자동 | StorageClass(k3s `local-path` 등) |
| 레지스트리 | `localhost:5001` | 사내 registry / 노드 로컬 |
| 도메인·TLS | `edu.localhost`(HTTP) | 실도메인 + cert-manager(내부 CA/ACME) |
| DB | 단일 postgres | (선택) CloudNativePG HA |
| 시크릿 | 자리표시자 | **즉시 교체**(EDU_JWT_SECRET 등) |
| GPU | — | NVIDIA GPU Operator |

전체 절차는 **[deploy/PRODUCTION.md](../../deploy/PRODUCTION.md)** 참고.

---

## 3. 확인 · 정리

```bash
./deploy/bootstrap.sh status        # 파드/서비스/인그레스 + 접속 URL
kubectl get pods -A                 # 전체 상태
./deploy/bootstrap.sh down          # 코어 제거(kind 는 클러스터 삭제)
```

---

## 4. 다른 실행 방법 · 관련 문서

- 로컬 개발(빠른 확인)은 K8s 없이 **docker-compose** 로도 된다:
  ```bash
  cp deploy/.env.example deploy/.env      # EDU_JWT_SECRET 등 채우기(≥32B)
  docker compose -f deploy/docker-compose.yml up -d --build
  cd frontend && npm install && npm run dev   # http://localhost:5173
  ```
- 인프라 전체 설명(도면): [system-overview.html](../../html/system-overview.html) · [deploy/infra-overview.html](../../deploy/infra-overview.html)
- 배포 파이프라인·모드(simulate/docker/real): [deploy/INFRA.md](../../deploy/INFRA.md) · [backend/README.md](../../backend/README.md)
- 서비스 규격(`service.yaml`·`resources.gpu`): [docs/MSA_SERVICE_SPEC.md](../architecture/MSA_SERVICE_SPEC.md)
- 보안·격리: [SECURITY.md](SECURITY.md)
