# Kubernetes 배포 가이드 · edu-msa

이 문서 하나로 **로컬 클러스터(kind) 리허설**부터 **플랫폼 배포**, **동적 서비스 배포(real 모드)**
까지 순서대로 따라 할 수 있습니다. (아래 "1. 로컬 리허설"은 실제로 검증된 절차입니다.)

## 구성 개요

- 네임스페이스 2개
  - `edu-platform` : 플랫폼 자체(프론트/백엔드/DB/…)
  - `edu-services` : **바이브 코더가 올린 서비스들이 뜨는 곳** (서비스마다 Pod 1개 + Service + Ingress)
- 하나의 클러스터가 모든 서비스를 함께 관리(스케줄링·복구·네트워킹)하고, **하나의 Ingress**
  (`https://edu.internal`)로 통합 노출한다. 개별 서비스는 `https://edu.internal/svc/<slug>`.

```
deploy/k8s/
├── namespaces.yaml            # edu-platform, edu-services
├── platform/                  # 플랫폼 매니페스트
│   ├── postgres.yaml · backend.yaml · frontend.yaml · ingress.yaml · rbac.yaml
└── service-template.yaml      # 서비스 1개당 렌더링되는 템플릿(백엔드가 값을 채움)
```

## 사전 준비

- `kubectl` (Docker Desktop에 포함) + 클러스터 하나
  - 로컬: **kind**(권장) / minikube / Docker Desktop 내장 Kubernetes
  - 실서버: 사내 쿠버네티스 클러스터 + kubeconfig

---

## 1. 로컬 리허설 — kind로 서비스 1개 띄우기 (검증됨)

바이브 코더가 올린 서비스가 어떻게 Pod로 뜨는지 그대로 재현하는 절차입니다.

```bash
# (1) kind 설치 (Windows 예시: 바이너리 다운로드)
#     https://kind.sigs.k8s.io/dl/v0.24.0/kind-windows-amd64  → kind.exe
# (2) 클러스터 생성
kind create cluster --name edu

# (3) 네임스페이스 적용
kubectl apply -f deploy/k8s/namespaces.yaml

# (4) 서비스 이미지 빌드 후 클러스터로 로드 (레지스트리 없이 로컬 이미지 사용)
docker build -t workdays:k8s ./examples/…/<서비스 폴더>   # 또는 git clone 한 레포
kind load docker-image workdays:k8s --name edu

# (5) Deployment + Service 적용 (service-template.yaml 을 채운 형태)
kubectl apply -f <렌더된 매니페스트>.yaml
kubectl rollout status deployment/workdays -n edu-services

# (6) 확인 (Ingress 컨트롤러가 없으면 port-forward)
kubectl get pods,svc -n edu-services
kubectl port-forward -n edu-services svc/workdays 8099:80
curl localhost:8099/healthz          # → ok
```

로컬 이미지를 쓰므로 매니페스트에 **`imagePullPolicy: Never`** 를 넣습니다.

> 검증 결과: Go로 만든 `workdays` 서비스가 `edu-services`에 Pod 1/1 Running,
> Service(ClusterIP)로 떠서 `/healthz`=ok, `/api/workdays`=영업일 21 응답 확인.

### (선택) Ingress로 하나의 진입점 만들기
kind에서 nginx ingress를 쓰려면 클러스터 생성 시 80/443 포트를 매핑하고 컨트롤러를 설치한다.
```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl -n ingress-nginx wait --for=condition=Ready pod -l app.kubernetes.io/component=controller --timeout=120s
# 이후 각 서비스의 Ingress(경로 /svc/<slug>)로 접근
```

---

## 2. 플랫폼(프론트/백엔드/DB) 배포

```bash
kubectl apply -f deploy/k8s/namespaces.yaml
kubectl apply -f deploy/k8s/platform/rbac.yaml
kubectl apply -f deploy/k8s/platform/postgres.yaml
kubectl apply -f deploy/k8s/platform/backend.yaml
kubectl apply -f deploy/k8s/platform/frontend.yaml
kubectl apply -f deploy/k8s/platform/ingress.yaml
```

- 이미지는 `registry.edu.internal/edu-msa-backend`,
  `registry.edu.internal/edu-msa-frontend`로 빌드·push 되어 있어야 한다.
  (로컬 kind면 위 (4)처럼 `kind load docker-image`로 대체 가능)
- `backend.yaml`은 `EDU_DEPLOY_MODE=real` + ServiceAccount `edu-deployer`(rbac.yaml)로
  `edu-services`에 배포 권한을 갖는다.
- 접근: `ingress.yaml`의 host(`edu.internal`) → `/` 프론트, `/api` 백엔드.

---

## 3. 동적 서비스 배포 (GitHub 레포 → Pod)

플랫폼 백엔드의 배포 파이프라인이 서비스마다 `service-template.yaml`을 렌더링해
`edu-services`에 적용한다. 모드(`EDU_DEPLOY_MODE`):

| 모드 | 동작 | 용도 |
| --- | --- | --- |
| `simulate` | 검증 + 매니페스트 렌더만 | 데모/미리보기 |
| `docker` | 호스트 Docker로 실제 컨테이너 기동(`localhost:31000+`) | 로컬 실배포·실증 |
| `real` | 이미지 빌드/푸시 + `kubectl apply`(K8s) | 실 클러스터 |

`real` 모드 흐름: `git clone → docker build → (registry) push → kubectl apply -n edu-services`.
승인 시 자동 배포(`edu.deploy.auto-on-approve=true`)면 관리자가 승인할 때 위 과정이 자동 실행된다.

> **프로덕션 주의**: 백엔드 Pod 안에서 이미지를 빌드하려면 호스트 도커 소켓 대신
> **인클러스터 빌더(Kaniko/BuildKit) + 이미지 레지스트리**를 붙이는 것이 표준이다.
> 현재 `real` 모드는 `docker build/push`가 가능한 환경(예: 도커 소켓이 있는 러너)을 전제로 한다.
> 규격은 [../../docs/MSA_SERVICE_SPEC.md](../../docs/MSA_SERVICE_SPEC.md).

## 4. 멀티테넌트 보안 하드닝 (필수 · 비신뢰 코드 실행)

여러 사람이 올린 임의 코드를 안전하게 격리 실행하기 위한 정책은 `hardening/`에 있다.
설계·근거는 [../../SECURITY.md](../../SECURITY.md) 참고.

```bash
kubectl apply -f deploy/k8s/hardening/00-namespaces-tiers.yaml     # 신뢰 등급별 NS + PodSecurity
kubectl apply -f deploy/k8s/hardening/10-resourcequota-limits.yaml # 자원 상한
kubectl apply -f deploy/k8s/hardening/20-networkpolicy.yaml        # 기본 차단(Calico/Cilium 필요)
kubectl apply -f deploy/k8s/hardening/30-runtimeclass-gvisor.yaml  # 샌드박스(노드에 gVisor 설치 시)
# 빌드는 40-kaniko-build-job.template.yaml 을 렌더해 Job 으로(도커 소켓 미사용)
```

- 내부 직원 서비스 → `edu-services`(baseline), 불특정 다수 → `edu-services-public`(restricted).
- 서비스 템플릿에 비루트·권한상승금지·capability drop·seccomp·SA토큰 미마운트가 이미 반영됨.

## 정리(cleanup)

```bash
kubectl delete namespace edu-services edu-platform
kind delete cluster --name edu
```
