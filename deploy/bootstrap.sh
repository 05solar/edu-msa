#!/usr/bin/env bash
# =============================================================================
# edu-msa 원커맨드 설치기
#
#   로컬 데모(kind):       ./deploy/bootstrap.sh up
#   실서버(기존 클러스터):  MODE=server DOMAIN=edu.example.go.kr REGISTRY=registry.example:5000 \
#                          ./deploy/bootstrap.sh up
#
# 하는 일:  클러스터 준비(kind) → 이미지 3종 빌드/푸시 → 코어 배포 → (선택)운영스택 → (선택)GPU
# 서브커맨드:  up | core | stack | gpu | images | status | down
# 주요 env:
#   MODE=kind|server        기본 kind (kind 클러스터 자동 생성 / server 는 현재 kubeconfig 사용)
#   DOMAIN=edu.localhost     플랫폼 접속 도메인 (kind 기본 edu.localhost, server 는 실도메인 지정)
#   REGISTRY=localhost:5001  이미지 레지스트리 접두어 (kind 기본 localhost:5001)
#   IMAGE_TAG=latest         이미지 태그
#   WITH_STACK=1|0           운영스택(모니터링·WAF·KEDA·로그·트레이스·cert-manager) 설치 (기본 up 에서 1)
#   WITH_GPU=1|0             NVIDIA GPU Operator 설치(테넌트 GPU 사용 시) (기본 0)
# =============================================================================
set -euo pipefail

# ---- 설정 -------------------------------------------------------------------
MODE="${MODE:-kind}"
CLUSTER="${CLUSTER:-edu}"
REG_NAME="${REG_NAME:-kind-registry}"
REG_PORT="${REG_PORT:-5001}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
WITH_STACK="${WITH_STACK:-auto}"     # auto = up 서브커맨드에서 1
WITH_GPU="${WITH_GPU:-0}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
K8S="$ROOT/deploy/k8s"

if [ "$MODE" = kind ]; then
  DOMAIN="${DOMAIN:-edu.localhost}"
  REGISTRY="${REGISTRY:-localhost:${REG_PORT}}"
  SCHEME="http"
else
  DOMAIN="${DOMAIN:-edu.internal}"
  REGISTRY="${REGISTRY:-registry.edu.internal}"
  SCHEME="https"
fi

# ---- 로깅 -------------------------------------------------------------------
c_i='\033[1;36m'; c_w='\033[1;33m'; c_e='\033[1;31m'; c_ok='\033[1;32m'; c_0='\033[0m'
log(){  printf "\n${c_i}▶ %s${c_0}\n" "$*"; }
ok(){   printf "${c_ok}✓ %s${c_0}\n" "$*"; }
warn(){ printf "${c_w}! %s${c_0}\n" "$*" >&2; }
die(){  printf "${c_e}✗ %s${c_0}\n" "$*" >&2; exit 1; }
need(){ command -v "$1" >/dev/null 2>&1 || die "필수 도구가 없습니다: $1 — 설치 후 다시 실행하세요."; }

# ---- 사전 점검 --------------------------------------------------------------
prereqs(){
  log "사전 도구 점검 (MODE=$MODE)"
  need docker; need kubectl; need git
  [ "$MODE" = kind ] && need kind
  docker info >/dev/null 2>&1 || die "docker 데몬에 연결할 수 없습니다. Docker 를 먼저 켜세요."
  ok "docker · kubectl · git${MODE:+ · }$( [ "$MODE" = kind ] && echo kind ) 확인"
}

# ---- kind 클러스터 + 로컬 레지스트리 + ingress-nginx ------------------------
ensure_kind(){
  # 1) 로컬 레지스트리 컨테이너
  if [ "$(docker inspect -f '{{.State.Running}}' "$REG_NAME" 2>/dev/null || true)" != true ]; then
    log "로컬 레지스트리 기동 (127.0.0.1:${REG_PORT} → registry:2)"
    docker run -d --restart=always -p "127.0.0.1:${REG_PORT}:5000" --name "$REG_NAME" registry:2 >/dev/null
  fi
  # 2) kind 클러스터 (containerd certs.d + 80/443 포트매핑 + ingress-ready 라벨)
  if ! kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
    log "kind 클러스터 생성: $CLUSTER"
    cat <<EOF | kind create cluster --name "$CLUSTER" --config -
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: $CLUSTER
containerdConfigPatches:
  - |-
    [plugins."io.containerd.grpc.v1.cri".registry]
      config_path = "/etc/containerd/certs.d"
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - { containerPort: 80,  hostPort: 80,  protocol: TCP }
      - { containerPort: 443, hostPort: 443, protocol: TCP }
EOF
  else
    ok "kind 클러스터 이미 존재: $CLUSTER"
  fi
  kubectl config use-context "kind-$CLUSTER" >/dev/null
  # 3) 노드 containerd 가 localhost:5001 → kind-registry:5000 로 해석
  local dir="/etc/containerd/certs.d/localhost:${REG_PORT}"
  for node in $(kind get nodes --name "$CLUSTER"); do
    docker exec "$node" mkdir -p "$dir"
    echo "[host.\"http://${REG_NAME}:5000\"]" | docker exec -i "$node" cp /dev/stdin "$dir/hosts.toml"
  done
  # 4) 레지스트리를 kind 네트워크에 연결
  if [ "$(docker inspect -f '{{json .NetworkSettings.Networks.kind}}' "$REG_NAME" 2>/dev/null || echo null)" = null ]; then
    docker network connect kind "$REG_NAME" 2>/dev/null || true
  fi
  # 5) ingress-nginx (kind provider · 핀 버전 · 호스트 80/443)
  if ! kubectl get ns ingress-nginx >/dev/null 2>&1; then
    log "ingress-nginx 설치 (kind provider)"
    kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.11.2/deploy/static/provider/kind/deploy.yaml
    kubectl -n ingress-nginx wait --for=condition=Available deploy/ingress-nginx-controller --timeout=180s || \
      warn "ingress-nginx 준비 대기 시간 초과 — 잠시 후 kubectl get pods -n ingress-nginx 로 확인하세요."
  else
    ok "ingress-nginx 이미 설치됨"
  fi
}

# ---- 이미지 3종 빌드 + 푸시 --------------------------------------------------
build_images(){
  log "이미지 빌드/푸시 → ${REGISTRY}/edu-msa-{backend,auth-service,frontend}:${IMAGE_TAG}"
  local svc
  for svc in backend auth-service frontend; do
    printf "  · %s 빌드\n" "$svc"
    docker build -q -t "${REGISTRY}/edu-msa-${svc}:${IMAGE_TAG}" "$ROOT/$svc" >/dev/null
    docker push "${REGISTRY}/edu-msa-${svc}:${IMAGE_TAG}" >/dev/null
  done
  ok "이미지 3종 준비 완료"
}

# ---- 코어 매니페스트 렌더링(sed) + 적용 -------------------------------------
apply_core(){
  log "코어 플랫폼 배포 (namespaces · DB · auth · backend · frontend · ingress)"
  local tmp; tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' RETURN
  local files=(
    "$K8S/namespaces.yaml"
    "$K8S/platform/rbac.yaml"
    "$K8S/platform/postgres.yaml"
    "$K8S/auth/auth-db.yaml"
    "$K8S/auth/auth-service.yaml"
    "$K8S/platform/backend.yaml"
    "$K8S/platform/frontend.yaml"
    "$K8S/platform/ingress.yaml"
  )
  local f base out
  for f in "${files[@]}"; do
    base="$(basename "$f")"; out="$tmp/$base"
    # 공통: 이미지 접두어 · 도메인 치환
    sed -e "s#registry.edu.internal/edu-msa-#${REGISTRY}/edu-msa-#g" \
        -e "s#edu\.internal#${DOMAIN}#g" "$f" > "$out"
    # backend 는 HA(CloudNativePG) 대신 코어 단일 postgres 를 쓰도록 DB 설정 치환
    if [ "$base" = backend.yaml ]; then
      sed -i.bak \
        -e "s#edu-db-rw\.edu-platform#postgres.edu-platform#g" \
        -e "s#name: edu-db-app, key: username#name: edu-db, key: POSTGRES_USER#g" \
        -e "s#name: edu-db-app, key: password#name: edu-db, key: POSTGRES_PASSWORD#g" \
        "$out" && rm -f "$out.bak"
    fi
    # kind(HTTP): CORS 오리진 스킴을 http 로 (같은 오리진이라 대개 무해하지만 명시적으로 맞춤)
    if [ "$MODE" = kind ]; then
      sed -i.bak -e "s#https://${DOMAIN}#http://${DOMAIN}#g" "$out" && rm -f "$out.bak"
    fi
  done
  # kind(HTTP): auth-service 의 Refresh 쿠키 Secure 를 끈다(HTTPS 아님 → Secure 쿠키는 전송 안 됨).
  # EDU_COOKIE_SECURE 다음 줄의 value: "true" 만 정밀 치환(다른 "true" 값은 건드리지 않음).
  if [ "$MODE" = kind ]; then
    sed -i.bak '/EDU_COOKIE_SECURE/{n;s/value: "true"/value: "false"/;}' "$tmp/auth-service.yaml" && rm -f "$tmp/auth-service.yaml.bak"
  fi

  kubectl apply -f "$tmp/namespaces.yaml"
  kubectl apply -f "$tmp/rbac.yaml"
  kubectl apply -f "$tmp/postgres.yaml"
  kubectl apply -f "$tmp/auth-db.yaml"
  kubectl apply -f "$tmp/auth-service.yaml"   # backend 보다 먼저 — edu-auth-jwt Secret 생성
  kubectl apply -f "$tmp/backend.yaml"
  kubectl apply -f "$tmp/frontend.yaml"
  kubectl apply -f "$tmp/ingress.yaml"

  log "롤아웃 대기 (최대 3분씩)"
  kubectl -n edu-platform rollout status deploy/auth-service --timeout=180s || warn "auth-service 대기 초과"
  kubectl -n edu-platform rollout status deploy/backend      --timeout=180s || warn "backend 대기 초과"
  kubectl -n edu-platform rollout status deploy/frontend     --timeout=120s || warn "frontend 대기 초과"
  ok "코어 배포 완료"

  if [ "$MODE" = server ]; then
    warn "실서버 보안: 매니페스트의 자리표시자 Secret 을 즉시 교체하세요 —"
    cat <<EOF
    kubectl -n edu-platform create secret generic edu-auth-jwt \\
      --from-literal=EDU_JWT_SECRET="\$(openssl rand -base64 48)" \\
      --from-literal=EDU_SEED_PASSWORD="\$(openssl rand -base64 12)" \\
      --dry-run=client -o yaml | kubectl apply -f -
    kubectl -n edu-platform rollout restart deploy/auth-service deploy/backend
EOF
  fi
}

# ---- 운영스택 (helm, 각 단계 best-effort) -----------------------------------
install_stack(){
  need helm
  log "운영스택 설치 (모니터링·KEDA·cert-manager·로그·트레이스) — 각 단계 best-effort"
  helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null 2>&1 || true
  helm repo add kedacore https://kedacore.github.io/charts >/dev/null 2>&1 || true
  helm repo add jetstack https://charts.jetstack.io >/dev/null 2>&1 || true
  helm repo add grafana https://grafana.github.io/helm-charts >/dev/null 2>&1 || true
  helm repo update >/dev/null 2>&1 || true

  _try(){ log "· $1"; shift; "$@" || warn "실패(건너뜀): $*"; }

  _try "cert-manager" helm upgrade --install cert-manager jetstack/cert-manager \
      -n cert-manager --create-namespace --set crds.enabled=true --wait --timeout 5m
  kubectl apply -f "$K8S/platform/edge/cert-manager/clusterissuers.yaml" 2>/dev/null || \
      warn "clusterissuers 적용 실패 — cert-manager CRD 준비 후 재적용하세요."

  _try "kube-prometheus-stack (Prometheus·Grafana·Alertmanager)" \
      helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
      -n monitoring --create-namespace --wait --timeout 8m
  kubectl apply -f "$K8S/platform/monitoring/prometheus-rules.yaml" 2>/dev/null || true
  kubectl apply -f "$K8S/platform/monitoring/backend-servicemonitor.yaml" 2>/dev/null || true

  _try "KEDA (scale-to-zero)" helm upgrade --install keda kedacore/keda \
      -n keda --create-namespace --wait --timeout 5m

  _try "Loki (로그)" helm upgrade --install loki grafana/loki-stack \
      -n logging --create-namespace --wait --timeout 6m

  _try "Tempo (트레이스)" helm upgrade --install tempo grafana/tempo \
      -n tracing --create-namespace --wait --timeout 5m

  ok "운영스택 설치 시도 완료 — 개별 상태는 kubectl get pods -A 로 확인하세요."
  warn "WAF(ModSecurity/CRS)는 ingress-nginx values 로 활성화합니다: deploy/k8s/platform/edge/README.md"
}

# ---- GPU Operator (테넌트 서비스가 nvidia.com/gpu 요청 시) -------------------
install_gpu(){
  need helm
  log "NVIDIA GPU Operator 설치 (테넌트 GPU 사용)"
  helm repo add nvidia https://helm.ngc.nvidia.com/nvidia >/dev/null 2>&1 || true
  helm repo update >/dev/null 2>&1 || true
  helm upgrade --install gpu-operator nvidia/gpu-operator \
      -n gpu-operator --create-namespace --wait --timeout 10m \
    || die "GPU Operator 설치 실패 — 노드에 NVIDIA 드라이버/컨테이너 툴킷·GPU 가 있는지 확인하세요."
  kubectl apply -f "$K8S/platform/gpu/nvidia-runtimeclass.yaml" 2>/dev/null || true
  ok "GPU Operator 설치 완료 — 확인: kubectl -n gpu-operator get pods; kubectl get nodes -o json | grep nvidia.com/gpu"
  warn "GPU 노드에 taint 가 있으면 서비스에 toleration 을 추가하세요: deploy/k8s/platform/gpu/README.md"
}

status(){
  echo "== edu-platform =="; kubectl -n edu-platform get pods,svc,ingress 2>/dev/null || true
  echo; echo "== 접속 =="; echo "  ${SCHEME}://${DOMAIN}"
}

access_hint(){
  echo
  ok "설치 완료"
  echo "  플랫폼 접속:  ${SCHEME}://${DOMAIN}"
  if [ "$MODE" = kind ]; then
    echo "  (브라우저는 *.localhost 를 자동으로 127.0.0.1 로 해석합니다. 안 되면 hosts 에 '127.0.0.1 ${DOMAIN}' 추가)"
  else
    echo "  DNS 의 ${DOMAIN} A레코드가 ingress-nginx 외부 IP 를 가리키게 하세요:"
    echo "     kubectl -n ingress-nginx get svc ingress-nginx-controller"
  fi
  echo "  상태 확인:   ./deploy/bootstrap.sh status"
}

down(){
  if [ "$MODE" = kind ]; then
    log "kind 클러스터 삭제: $CLUSTER"
    kind delete cluster --name "$CLUSTER" || true
    docker rm -f "$REG_NAME" 2>/dev/null || true
    ok "삭제 완료"
  else
    log "서버 코어 네임스페이스 제거 (운영스택은 수동)"
    kubectl delete -f "$K8S/platform/ingress.yaml" 2>/dev/null || true
    kubectl delete ns edu-platform edu-services 2>/dev/null || true
    ok "코어 제거 완료 (helm 스택은 helm uninstall 로 개별 제거)"
  fi
}

# ---- 엔트리포인트 -----------------------------------------------------------
cmd="${1:-up}"
case "$cmd" in
  up)
    prereqs
    [ "$MODE" = kind ] && ensure_kind
    build_images
    apply_core
    [ "$WITH_STACK" = 1 ] || { [ "$WITH_STACK" = auto ] && WITH_STACK=1; }
    [ "$WITH_STACK" = 1 ] && install_stack
    [ "$WITH_GPU" = 1 ] && install_gpu
    access_hint
    ;;
  core)   prereqs; [ "$MODE" = kind ] && ensure_kind; build_images; apply_core; access_hint ;;
  images) prereqs; build_images ;;
  stack)  install_stack ;;
  gpu)    install_gpu ;;
  status) status ;;
  down)   down ;;
  *) die "알 수 없는 명령: $cmd  (up|core|stack|gpu|images|status|down)";;
esac
