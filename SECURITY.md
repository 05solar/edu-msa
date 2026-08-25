# SECURITY.md · 멀티테넌트 보안 하드닝 (내부 직원 + 불특정 다수)

이 문서는 "여러 사람이 올린 임의의 코드를 하나의 쿠버네티스 클러스터에서 실행"하는
본 플랫폼의 **보안·격리 설계**와, 그에 맞춰 실제로 적용한 작업을 설명한다.

## 1. 신뢰 모델

| 대상 | 신뢰 수준 | 배치 네임스페이스 | 강제 정책 |
| --- | --- | --- | --- |
| 내부 직원이 올린 서비스 | 반신뢰(semi-trusted) | `edu-services` | PodSecurity **baseline** 강제 |
| 불특정 다수가 올린 서비스 | 비신뢰(untrusted) | `edu-services-public` | PodSecurity **restricted** 강제 + 샌드박스 권장 |

핵심 원칙: **한 서비스가 플랫폼·DB·다른 서비스·노드에 손댈 수 없어야 한다.**

## 2. 적용한 것 (deploy/k8s/hardening/)

| 파일 | 내용 | 목적 |
| --- | --- | --- |
| `00-namespaces-tiers.yaml` | 신뢰 등급별 네임스페이스 2개 + PodSecurity 라벨 | 등급 분리·비루트 등 강제 |
| `10-resourcequota-limits.yaml` | 네임스페이스별 ResourceQuota + LimitRange | 자원 독식 방지·기본 한도 |
| `20-networkpolicy.yaml` | 기본 차단(deny-by-default) + DNS/Ingress만 허용, 사설망·메타데이터 차단 | 네트워크 격리 |
| `30-runtimeclass-gvisor.yaml` | 샌드박스 런타임(gVisor) RuntimeClass | 커널 공격면 격리(비신뢰) |
| `40-kaniko-build-job.template.yaml` | 인클러스터 Kaniko 빌드 Job 템플릿 | **docker.sock 없이** 안전 빌드 |

추가로 **서비스 매니페스트 템플릿**(`deploy/k8s/service-template.yaml`,
`backend/.../deploy-templates/service-template.yaml`)에 컨테이너 보안 컨텍스트를 강화:
- `runAsNonRoot: true`, `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`,
  `seccompProfile: RuntimeDefault`, `automountServiceAccountToken: false`
- 비신뢰 tier는 `runtimeClassName: gvisor` 로 샌드박스 실행(주석 해제)

### 각 정책이 막는 것
- **PodSecurity(restricted)**: 루트 실행·권한 상승·특권 컨테이너·호스트 네임스페이스·
  위험 capability·비표준 seccomp 차단. → 비신뢰 코드가 노드를 장악하지 못하게.
- **NetworkPolicy(deny-by-default)**: 기본 모든 통신 차단 후, Ingress 컨트롤러 인입과
  DNS만 허용. 내부 tier는 공용 인터넷은 허용하되 **사설망(10/172.16/192.168)과
  메타데이터(169.254.0.0/16)** 차단 → 플랫폼/DB/클라우드 메타데이터 접근 봉쇄.
  공개 tier는 DNS 외 egress 없음(완전 격리).
- **ResourceQuota/LimitRange**: 한 서비스가 CPU/메모리/파드 수를 독식하지 못하게.
- **Kaniko 빌드**: 호스트 도커 소켓(=사실상 루트) 노출 없이 사용자 공간에서 이미지 빌드.

## 3. 검증 (로컬 kind)

- `edu-services-public`(restricted)에 루트 busybox 생성 시도 → **PodSecurity가 거부**
  (allowPrivilegeEscalation/capabilities/runAsNonRoot/seccompProfile 위반 명시).
- 내부 tier의 비루트 서비스(`workdays`, Go)는 정상 Running 유지.
- ResourceQuota/LimitRange/NetworkPolicy/RuntimeClass 오브젝트 정상 생성.

## 4. 적용 순서

```bash
kubectl apply -f deploy/k8s/hardening/00-namespaces-tiers.yaml
kubectl apply -f deploy/k8s/hardening/10-resourcequota-limits.yaml
kubectl apply -f deploy/k8s/hardening/20-networkpolicy.yaml
kubectl apply -f deploy/k8s/hardening/30-runtimeclass-gvisor.yaml   # 노드에 gVisor 설치 시
# 빌드가 필요할 때 40-kaniko-build-job.template.yaml 을 렌더링해 Job 생성
```

## 5. 프로덕션에서 반드시 채워야 할 것 (아직 아님)

- **NetworkPolicy 강제 CNI**: kind 기본(kindnet)은 미강제 → 실서버는 **Calico/Cilium**.
- **gVisor/Kata 노드 설치**: `30-runtimeclass` 사용 전 노드 런타임 준비.
- **백엔드 real 모드 배선**: docker 소켓 빌드 → **Kaniko + 이미지 레지스트리**로 교체,
  업로더 신뢰도에 따라 배포 네임스페이스(`edu-services` vs `edu-services-public`) 자동 선택.
- **오토스케일/비용**: HPA + Cluster Autoscaler, 유휴 서비스 **scale-to-zero(Knative/KEDA)**.
- **플랫폼 HA**: 백엔드 무상태+복제, 관리형 HA PostgreSQL, 시크릿 관리(Vault/Sealed Secrets).
- **관측성**: 로그/메트릭/감사(감사 로그, 이미지 스캐닝, 정책 위반 알림).

자세한 K8s 배포 절차는 [deploy/k8s/README.md](deploy/k8s/README.md),
인프라 작업 규칙은 [deploy/AGENT.md](deploy/AGENT.md) 참조.
