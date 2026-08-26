# PRODUCTION.md · 실서버(GPU 박스) 배포 가이드

로컬 kind 데모를 실제 서버로 옮기는 전체 절차. 요약은 **"클러스터 하나 만들고, 이미지 레지스트리
정하고, 도메인·TLS 붙이고, `bootstrap.sh` 를 server 모드로 돌린다"** 이다.

> 중요: 이 플랫폼 **코어는 GPU가 필요 없다(CPU 전용)**. GPU는 배포되는 **테넌트 서비스**가
> `service.yaml` 에서 `resources.gpu>=1` 로 요청할 때만 쓰인다 → [k8s/platform/gpu/README.md](k8s/platform/gpu/README.md).

---

## 0. 한눈에 (원커맨드까지 4단계)

```bash
# ① 클러스터 (서버에 k8s 가 아직 없다면 k3s 권장)
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik" sh -
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml            # 또는 ~/.kube/config 로 복사

# ② (테넌트 GPU 쓸 때만) GPU Operator
WITH_GPU=1 ./deploy/bootstrap.sh gpu

# ③ 코어 + 운영스택 한 번에
MODE=server DOMAIN=edu.example.go.kr REGISTRY=<레지스트리 접두어> \
  WITH_STACK=1 ./deploy/bootstrap.sh up

# ④ DNS: edu.example.go.kr → ingress 외부 IP
kubectl -n ingress-nginx get svc ingress-nginx-controller
```

kind(로컬)과 **똑같은 스크립트**다. 차이는 `MODE=server`, 실제 `DOMAIN`, 실제 `REGISTRY` 뿐.

---

## 1. 클러스터 준비

서버에 k8s가 없으므로 하나 만든다. GPU 단일 서버라면 **k3s** 가 가장 간단하다.

```bash
# k3s (내장 traefik 은 끈다 — 우리는 ingress-nginx 사용)
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik --flannel-backend=none --disable-network-policy" sh -
```
- `--flannel-backend=none --disable-network-policy` 로 기본 CNI를 끄고 **Calico** 를 설치한다
  (NetworkPolicy 실제 강제; 신뢰등급 격리에 필수).
  ```bash
  kubectl create -f https://raw.githubusercontent.com/projectcalico/calico/v3.28.0/manifests/tigera-operator.yaml
  kubectl create -f https://raw.githubusercontent.com/projectcalico/calico/v3.28.0/manifests/custom-resources.yaml
  ```
  > NetworkPolicy 강제가 당장 필요 없으면 위 두 플래그를 빼고 k3s 기본 CNI로 시작해도 된다.
- kubeadm/매니지드(GKE·EKS 등)라면 그 클러스터의 kubeconfig 를 쓰면 된다. 나머지 절차 동일.

스토리지: k3s 는 `local-path` StorageClass 가 기본 제공되어 PVC(postgres 등)가 바로 뜬다.
매니지드는 기본 StorageClass 가 있으니 그대로 사용.

---

## 2. 이미지 레지스트리

`REGISTRY` 는 노드가 pull 할 수 있는 이미지 접두어다. 택1:
- **사내 레지스트리**: `REGISTRY=registry.example.go.kr:5000` (인증 필요 시 노드에 pull secret).
- **k3s 임베디드/로컬**: 간단히는 `docker save | k3s ctr images import` 로 노드에 직접 적재.
- **매니지드**: GAR/ECR 등. `bootstrap.sh images` 가 `REGISTRY/edu-msa-*` 로 push 한다.

`bootstrap.sh up` 이 `images` 단계에서 backend·auth-service·frontend 3종을 빌드·push 한다.
(빌드 호스트에 docker 필요. 클러스터 노드와 빌드 호스트가 다르면 REGISTRY 는 양쪽에서 도달 가능해야 함.)

---

## 3. 도메인 · TLS

- `DOMAIN` 의 A레코드를 ingress-nginx 외부 IP로:
  ```bash
  kubectl -n ingress-nginx get svc ingress-nginx-controller   # EXTERNAL-IP 확인
  ```
- TLS: 운영스택이 **cert-manager** 를 깔고 `ClusterIssuer` 를 만든다. 사내망은 내부 CA(`edu-ca`),
  공인 도메인은 `clusterissuers.yaml` 의 `edu-ca` 를 **Let's Encrypt(ACME)** 발급자로 교체하면
  Ingress 주석만으로 공인 인증서가 자동 발급된다. (edge/cert-manager/README.md)

---

## 4. 데이터베이스 (코어 → HA 업그레이드)

`bootstrap.sh` 코어는 **단일 postgres**(`platform/postgres.yaml`)로 뜬다 — 어디서나 바로 동작.
운영 HA가 필요하면 **CloudNativePG** 로 승격한다.

```bash
# CNPG 오퍼레이터
kubectl apply -f https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/release-1.24/releases/cnpg-1.24.0.yaml
# 3-인스턴스 클러스터 + edu-db-rw 라우팅
kubectl apply -f deploy/k8s/platform/postgres-ha.yaml
# backend 를 edu-db-rw / edu-db-app 시크릿으로 전환 (오퍼레이터가 시크릿 생성)
kubectl -n edu-platform set env deploy/backend \
  DB_URL=jdbc:postgresql://edu-db-rw.edu-platform.svc.cluster.local:5432/edumsa
# DB_USER/DB_PASSWORD 는 edu-db-app 시크릿을 참조하도록 backend.yaml 원본을 적용해도 된다
```

---

## 5. 시크릿 (실서버 필수)

코어는 매니페스트에 **자리표시자 Secret** 을 담아 바로 뜨지만, 실서버에서는 즉시 교체한다.
```bash
kubectl -n edu-platform create secret generic edu-auth-jwt \
  --from-literal=EDU_JWT_SECRET="$(openssl rand -base64 48)" \
  --from-literal=EDU_SEED_PASSWORD="$(openssl rand -base64 12)" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n edu-platform rollout restart deploy/auth-service deploy/backend
```
`EDU_JWT_SECRET` 은 auth-service(발급)와 backend(검증)가 **같은 값**을 봐야 한다(둘 다 `edu-auth-jwt`).

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
