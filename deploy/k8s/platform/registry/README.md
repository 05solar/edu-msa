# 이미지 레지스트리 pull 경로 (kind local registry)

P1-1에서 Kaniko가 docker.sock 없이 이미지를 빌드·push하는 것을 검증했다.
P3-4는 그 이미지를 **노드(kubelet/containerd)가 실제로 pull해 파드를 기동**하는 경로를 완성한다.

## 왜 필요한가
인클러스터 레지스트리(ClusterIP Service)는 kubelet이 해석하지 못한다(kubelet은 클러스터 DNS를
쓰지 않음). 노드에서 해석 가능한 레지스트리 엔드포인트 + containerd 설정이 필요하다.

## 구성 (kind)
`setup-local-registry.sh` 실행 — 다음을 수행:
1. 레지스트리 컨테이너 `kind-registry`(호스트 `127.0.0.1:5001` → 컨테이너 `5000`).
2. `kind-with-registry.yaml`로 클러스터 생성(`containerdConfigPatches`가 `config_path=/etc/containerd/certs.d`).
3. 각 노드에 `/etc/containerd/certs.d/localhost:5001/hosts.toml` → `http://kind-registry:5000`.
4. 레지스트리를 `kind` 도커 네트워크에 연결(노드에서 도달).

이후 파드/매니페스트는 이미지를 **`localhost:5001/<repo>:<tag>`** 로 참조한다.

## push/pull 비대칭 주의
- **kubelet pull**: `localhost:5001/...` → containerd가 hosts.toml로 `kind-registry:5000`에 해석.
- **파드(예: Kaniko) push**: 파드 안에서 `localhost`는 파드 자신이므로 사용 불가.
  파드는 레지스트리에 도달 가능한 이름/IP로 push해야 한다 —
  본 검증은 Kaniko 파드에 `hostAliases`(`kind-registry` → 레지스트리 IP)를 주고
  `--destination=kind-registry:5000/<repo>` 로 push했다. 레지스트리 저장은 repo 경로 기준이라
  `localhost:5001/<repo>` 로 pull해도 동일 이미지에 매칭된다.

## 실서버(비 kind)
- 사내 레지스트리(Harbor 등) 또는 클라우드 레지스트리(ECR/GCR/ACR)를 쓰고,
  노드 containerd에 인증/미러 설정. Kaniko는 레지스트리 자격증명 시크릿으로 push.
- 백엔드 real 모드는 이미 `edu.deploy.registry`로 대상 레지스트리를 주입한다(P1-1).

## 동작 검증 (kind, 2026-08-25) — end-to-end
1. Kaniko가 실제 GitHub 레포(test-code, Go)를 docker.sock 없이 빌드 →
   `kind-registry:5000/workdays:v1` push 성공(digest `sha256:a3c56d8…`).
2. `localhost:5001/workdays:v1` 참조 Deployment(imagePullPolicy: Always) 적용 →
   kubelet **"Successfully pulled image localhost:5001/workdays:v1 in 221ms"**,
   Image ID digest가 **push한 digest와 동일**, 파드 1/1 Running.
3. 서비스 호출: `GET /` → 200(근무일수 계산기 HTML), `GET /healthz` → 200.
- 결론: GitHub 레포 → 인클러스터 빌드 → 레지스트리 → 노드 pull → 서비스 기동 전 구간 확인.

## 참고: 재생성 시 다른 스택
이 클러스터를 재생성하면 다른 플랫폼 스택은 각 README의 helm 명령으로 재설치:
Calico(`../../README`) · KEDA(`../scale-to-zero`) · kube-prometheus-stack(`../monitoring`) ·
ingress-nginx(`../edge`) · cert-manager(`../edge/cert-manager`) · Loki(`../logging`) · Tempo(`../tracing`).
