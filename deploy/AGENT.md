# AGENT.md · 인프라(deploy) 작업 규칙

루트 [../AGENT.md](../AGENT.md)를 상속한다. 아래는 인프라 고유 규칙이다.

## 보안 최우선 규칙 (멀티테넌트 · 비신뢰 코드 전제)

1. **기본 차단(deny-by-default)**: NetworkPolicy는 전부 막고 필요한 것만 연다.
   업로드 서비스는 플랫폼·DB·클라우드 메타데이터(169.254.0.0/16)·사설망에 접근 금지.
2. **비루트·최소 권한**: 모든 워크로드는 `runAsNonRoot`, `allowPrivilegeEscalation:false`,
   `capabilities.drop:[ALL]`, `seccompProfile:RuntimeDefault`. 특권 컨테이너·hostPath·
   hostNetwork 금지. `automountServiceAccountToken:false`.
3. **신뢰 등급 분리**: 내부 직원=`edu-services`(baseline), 불특정 다수=`edu-services-public`
   (restricted + gVisor). 신뢰도에 따라 배포 네임스페이스를 선택한다.
4. **빌드에 docker.sock 금지(프로덕션)**: 인클러스터 빌드는 **Kaniko/BuildKit(rootless)**
   + 레지스트리. 호스트 도커 소켓 마운트는 로컬 개발 전용.
5. **자원 상한 필수**: 네임스페이스마다 ResourceQuota + LimitRange.
6. **시크릿은 매니페스트에 평문 금지**: Secret/외부 시크릿 매니저 사용. JWT 서명키
   `EDU_JWT_SECRET`(≥32B)는 `edu-auth-jwt` Secret 한 곳에서 관리하고 auth-service(발급)·
   backend(검증)가 **같은 값**을 공유한다. 로컬은 `deploy/.env`(커밋 금지).

## 배포 모드

- `simulate`(기본·데모) / `docker`(로컬 실배포) / `real`(K8s). 자세한 내용은
  [INFRA.md](INFRA.md)·[k8s/README.md](k8s/README.md), 하드닝 설계는 [../SECURITY.md](../docs/operations/SECURITY.md).

## 작업 후 갱신할 문서

- `deploy/PROCESS.md` — 변경 이력(필수).
- 정책/구조 변경 시 `deploy/k8s/README.md`, `../SECURITY.md`.

## 검증

- 로컬 kind로 `kubectl apply` 성공 + 정책 동작(예: restricted 네임스페이스에서 루트 파드 거부) 확인.
- NetworkPolicy 강제는 Calico/Cilium 등 지원 CNI에서 확인(kindnet 미강제).
