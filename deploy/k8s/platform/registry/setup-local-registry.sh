#!/bin/sh
# kind + 로컬 레지스트리 구성 (containerd certs.d 패턴).
# 참고: https://kind.sigs.k8s.io/docs/user/local-registry/
set -o errexit

reg_name='kind-registry'
reg_port='5001'
cluster='edu'

# 1) 레지스트리 컨테이너 (호스트 127.0.0.1:5001 -> 컨테이너 5000)
if [ "$(docker inspect -f '{{.State.Running}}' "${reg_name}" 2>/dev/null || true)" != 'true' ]; then
  docker run -d --restart=always -p "127.0.0.1:${reg_port}:5000" --name "${reg_name}" registry:2
fi

# 2) 레지스트리 지원 클러스터 생성 (containerdConfigPatches) — 없을 때만
if ! kind get clusters | grep -qx "${cluster}"; then
  kind create cluster --name "${cluster}" --config kind-with-registry.yaml
fi

# 3) 각 노드에 hosts.toml: localhost:5001 -> http://kind-registry:5000
REGISTRY_DIR="/etc/containerd/certs.d/localhost:${reg_port}"
for node in $(kind get nodes --name "${cluster}"); do
  docker exec "${node}" mkdir -p "${REGISTRY_DIR}"
  echo '[host."http://kind-registry:5000"]' | docker exec -i "${node}" cp /dev/stdin "${REGISTRY_DIR}/hosts.toml"
done

# 4) 레지스트리를 kind 네트워크에 연결(노드에서 kind-registry:5000 도달)
if [ "$(docker inspect -f='{{json .NetworkSettings.Networks.kind}}' "${reg_name}")" = 'null' ]; then
  docker network connect kind "${reg_name}"
fi

# 5) 레지스트리 위치 안내 ConfigMap
kubectl apply -f - <<'EOF'
apiVersion: v1
kind: ConfigMap
metadata:
  name: local-registry-hosting
  namespace: kube-public
data:
  localRegistryHosting.v1: |
    host: "localhost:5001"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
EOF

echo "완료. 이미지: 파드/매니페스트는 localhost:5001/<repo>:<tag> 로 참조."
