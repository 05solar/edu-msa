# SECURITY.md · 멀티테넌트 보안 하드닝 (내부 직원 + 불특정 다수)

이 문서는 "여러 사람이 올린 임의의 코드를 하나의 쿠버네티스 클러스터에서 실행"하는
본 플랫폼의 **보안·격리 설계**와, 그에 맞춰 실제로 적용한 작업을 설명한다.

## 1. 신뢰 모델

| 대상 | 신뢰 수준 | 배치 네임스페이스 | 강제 정책 |
| --- | --- | --- | --- |
| 내부 직원이 올린 서비스 | 반신뢰(semi-trusted) | `edu-services` | PodSecurity **baseline** 강제 |
| 불특정 다수가 올린 서비스 | 비신뢰(untrusted) | `edu-services-public` | PodSecurity **restricted** 강제 + 샌드박스 권장 |

핵심 원칙: **한 서비스가 플랫폼·DB·다른 서비스·노드에 손댈 수 없어야 한다.**

## 2. 인증·인가 (auth-service)

계정·토큰·권한은 별도 마이크로서비스 `auth-service`(자체 `auth-db`)가 단일 소스로 관리한다.

- **JWT 자체 검증**: 로그인 시 발급한 HS256 JWT 를 각 자원 서버(backend 등)가 동일한
  `EDU_JWT_SECRET` 으로 직접 검증한다. 요청마다 auth-service 를 동기 호출하지 않으므로
  인증 서비스가 단일 장애점·병목이 되지 않는다.
- **토큰 보관**: Access 토큰은 응답 본문(프론트 메모리)에만 두고, Refresh 토큰은
  `HttpOnly` 쿠키(`edu_refresh`, `path=/api/auth`, 회전)로만 내려 XSS 로 탈취되지 않게 한다.
- **RBAC**: backend `SecurityConfig` 가 경로별 역할을 강제한다 — 공개(`/api/health`·
  `/api/catalog/**`) / 로그인(`/api/programs`) / `CODER` 이상(`POST /api/programs`) /
  `ADMIN`(`/api/programs/all`·`*/deploy`·`/api/users`).
- **최소 권한 + 승인**: 회원가입은 **항상 `USER`** 로 생성된다. 상향 권한(`CODER`/`ADMIN`)은
  신청(가입 시 `requestRole` 또는 로그인 후 `role-request`)만 접수되고, 운영 관리자가
  승인해야 실제로 부여된다. **자가 가입/신청만으로는 권한이 오르지 않는다.**
- **서명 키**: `EDU_JWT_SECRET`(≥32B)은 발급/검증 공용 키라 노출 시 토큰 위조가 가능하다.
  로컬은 `deploy/.env`, K8s 는 `edu-auth-jwt` Secret 으로 주입하며, 프로덕션은 Vault/Sealed
  Secrets 로 관리·회전한다(6절).

## 3. 적용한 것 (deploy/k8s/hardening/)

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

## 4. 검증 (로컬 kind)

- `edu-services-public`(restricted)에 루트 busybox 생성 시도 → **PodSecurity가 거부**
  (allowPrivilegeEscalation/capabilities/runAsNonRoot/seccompProfile 위반 명시).
- 내부 tier의 비루트 테스트 서비스(Go)는 정상 Running 유지.
- ResourceQuota/LimitRange/NetworkPolicy/RuntimeClass 오브젝트 정상 생성.

## 5. 적용 순서

```bash
kubectl apply -f deploy/k8s/hardening/00-namespaces-tiers.yaml
kubectl apply -f deploy/k8s/hardening/10-resourcequota-limits.yaml
kubectl apply -f deploy/k8s/hardening/20-networkpolicy.yaml
kubectl apply -f deploy/k8s/hardening/30-runtimeclass-gvisor.yaml   # 노드에 gVisor 설치 시
# 빌드가 필요할 때 40-kaniko-build-job.template.yaml 을 렌더링해 Job 생성
```

## 6. 프로덕션에서 반드시 채워야 할 것 (아직 아님)

- **gVisor/Kata 노드 설치**: `30-runtimeclass` 사용 전 노드 런타임 준비.
- **감사로그/실 OTel 계측**: 로그에 trace_id 표준화, 앱 자동/수동 계측, 감사로그 별도 보존.
- **JWT 서명 키 관리**: `EDU_JWT_SECRET` 을 Vault/Sealed Secrets 로 이관·주기적 회전(현재는
  `.env`/K8s `edu-auth-jwt` Secret 주입). 노출 시 토큰 위조가 가능하므로 최우선 항목.

### 완료된 항목 (구현·검증)
- **레지스트리 pull 경로** (P3-4): kind-local-registry(containerd certs.d). Kaniko push 이미지를
  노드가 pull해 파드 기동까지 end-to-end 검증(digest 일치, 서비스 200). 실서버는 사내/클라우드 레지스트리로 교체.
- **자동 TLS** (P3-1): cert-manager 발급자 체인(self-signed 루트 CA→edu-ca) + Ingress ingress-shim.
  kind 검증: 주석만으로 인증서+시크릿 자동 발급(우리 CA 서명). 실서버는 ACME로 교체.
- **엣지 보안** (P2-3): ingress-nginx + ModSecurity/OWASP CRS. TLS 종료, 서비스별 rate-limit, WAF.
  kind 검증: 정상 200, XSS/SQLi/경로탐색 403, rate-limit 503. 미신뢰(공개) 서비스 경계 강화.
- **관측성** (P2-2): kube-prometheus-stack. 백엔드 `/actuator/prometheus`(micrometer) + ServiceMonitor.
  kind 검증: up 11타깃, 백엔드 메트릭 200, ServiceMonitor 디스커버리→up=1.
- **유휴 비용/scale-to-zero** (P2-1): KEDA HTTP add-on. 유휴 서비스 0 축소, 요청 시 0→1 콜드스타트.
  kind 검증: replicas 0→1, HTTP 200(2.85s). 인터셉터 경유 라우팅 필요.
- **가용성** (P1-3): 테넌트 서비스 템플릿에 무중단 롤링(maxUnavailable:0/maxSurge:1) + PDB(maxUnavailable:1)
  + AZ 분산(topologySpread ScheduleAnyway)/노드 안티어피니티(soft). kind에서 롤링 무중단·PDB 검증.
  주의: maxUnavailable:0 서지(+1)를 위해 네임스페이스 ResourceQuota가 파드 N+1을 허용해야 함.
- **NetworkPolicy 강제 CNI** (P1-2): kind에 **Calico** 적용, default-deny + tier별 egress 대조 검증.
- **백엔드 real 모드 배선** (P1-1): docker 소켓 빌드 제거 → **Kaniko 인클러스터 Job**으로 교체.
  업로더 `repoUrl`/`branch`는 정규식 검증 후에만 Kaniko `--context`에 삽입(인자·YAML 주입 차단).
  업로더 신뢰도에 따라 배포 네임스페이스(`edu-services` vs `edu-services-public`) **fail-closed** 자동 선택.
  검증: kind에서 Kaniko가 실제 GitHub 레포를 docker.sock 없이 빌드→레지스트리 push 성공.
- **플랫폼 HA**: 백엔드 무상태+복제, 관리형 HA PostgreSQL, 시크릿 관리(Vault/Sealed Secrets).
- **관측성**: 로그/메트릭/감사(감사 로그, 이미지 스캐닝, 정책 위반 알림).

자세한 K8s 배포 절차는 [deploy/k8s/README.md](deploy/k8s/README.md),
인프라 작업 규칙은 [deploy/AGENT.md](deploy/AGENT.md) 참조.
