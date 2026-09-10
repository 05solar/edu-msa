# PRODUCTION_INFRA_REQUIREMENTS.md · Production 인프라 요구사항 (담당자 전달용)

> 작성일: 2026-09-10 · 기준 커밋: `43e3f92` (배포 후보 `2737bdc`)
> 목적: **인프라/운영 담당자가 이 문서만으로 Production 환경을 준비**할 수 있게 한다.
> 배경: 코드·설정·staging(kind 멀티노드) 검증은 완료(CONDITIONAL GO,
> [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md) §12). 배포가 막힌 이유는 코드가 아니라
> **실제 운영 클러스터와 운영 알림 수신처가 없기 때문**이다. 이 문서의 체크리스트(§14)가
> 완료되고 §15의 정보가 개발팀에 전달되면, 검증된 절차로 즉시 배포를 재개한다.

---

## 1. 현재 상태

| 항목 | 값 |
|---|---|
| 판정 | **CONDITIONAL GO / BLOCKED BY INFRA** |
| 코드·설정 | 완료 — main == origin/main, working tree clean |
| staging 검증 | 리허설 런북 전 단계 실측 완주(백업·PITR 41s·failover 9s/RPO 0·E2E·부하·경보 체인) |
| 미충족 | ① Production Kubernetes 클러스터 ② 운영 Alertmanager 수신처 endpoint |

## 2. 배포 이미지 (확정 — 변경 금지)

`IMAGE_TAG=git-2737bdc` (`:latest` 미사용, digest 고정 검증 완료):

| Service | Image | Digest |
|---|---|---|
| backend / backend-worker(동일 이미지) | `ghcr.io/05solar/edu-msa/edu-msa-backend:git-2737bdc` | `sha256:a13dcb7def21b8329014a40595218787007df89b0d17274e96161b6684ebd243` |
| auth-service | `ghcr.io/05solar/edu-msa/edu-msa-auth-service:git-2737bdc` | `sha256:8e116caf361908026fd96145df40a61787d29829a0a46c7faa18a2ea0cfe0355` |
| frontend | `ghcr.io/05solar/edu-msa/edu-msa-frontend:git-2737bdc` | `sha256:9e031afdb37cbbcfdd83e5ab14c455f91e0e3b46d3a74ea958c170ecf8bae1c9` |

## 3. Kubernetes 클러스터 요구사항

| 항목 | 요구 | 근거 |
|---|---|---|
| 형태 | Production/production-like K8s — **매니지드** 또는 HA 컨트롤플레인(3대, 예: k3s embedded etcd) | deploy/PRODUCTION.md §1 |
| 워커 | **3대 이상**, 물리/VM 분산(단일 물리 서버 위 멀티노드 금지) | 무중단 롤링·topologySpread·PDB 가 노드 분산을 전제 |
| 버전 | 검증 기준 v1.37 (±근접 버전) | staging 검증 기준 |
| PodSecurity | namespace 라벨 기반 PSA 지원(baseline/restricted) | hardening 매니페스트 |
| (선택) gVisor | 비신뢰 테넌트 RuntimeClass | hardening/30-runtimeclass |

## 4. Node/Workload 리소스 기준 (현재 manifest 값 — Production 초기값)

> 아래는 staging 실측을 반영해 확정된 **초기값**이다. 실서버 실측 전 "충분"을 단정하지 않는다 —
> 배포 후 §관측성으로 재보정한다.

| Workload | Min Replica | Max Replica | CPU Req | CPU Limit | Mem Req | Mem Limit |
|---|---:|---:|---|---|---|---|
| backend | 2 (HPA) | 10 | 250m | 1 | 512Mi | 1Gi |
| auth-service | 2 (HPA) | 6 | 200m | 1 | 384Mi | 768Mi |
| backend-worker | 1 (KEDA) | 5 | 200m | 1 | 512Mi | 1Gi |
| frontend | 2 (HPA) | 8 | 50m | 250m | 64Mi | 128Mi |
| Redis | 1 | 1 | 100m | 500m | 128Mi | 512Mi |
| platform PostgreSQL (CNPG ×3) | 3 | 3 | 1 | 2 | 2Gi | 4Gi |
| auth PostgreSQL (CNPG ×3) | 3 | 3 | 500m | 2 | 1Gi | 2Gi |
| PgBouncer Pooler (4종 ×2) | 8 | 8 | (기본) | — | — | — |

합산 대략치(최소 기동): requests ≈ 7 CPU / 13Gi + 관측 스택 + ingress — **워커 3대 × 8 CPU/16Gi
이상 권장**(테넌트 Quota: edu-services 8 CPU/16Gi + public 4 CPU/8Gi 별도).
참고 실측: 로그인은 bcrypt CPU 바운드로 auth replica(1 CPU)당 지속 ~16/s — HPA max 6 은
피크 ~96/s 설계.

## 5. Storage 요구사항 (필수 — 미충족 시 배포 불가)

**`local-path`(노드 종속 스토리지) 금지.** 노드 장애 시 다른 노드에서 PVC 를 재연결할 수 있는
**네트워크/분산 block StorageClass** 가 필수다. 특정 제품 강제 없음 — 필수 특성:
RWO 지원 · 노드 재스케줄 시 재연결 · 동적 프로비저닝. (예시 선택지: Longhorn, Rook/Ceph,
클라우드 CSI(EBS/PD/AzureDisk 등), SAN 기반 CSI.)

| Workload | PVC | 현재 설정(운영 프로필) | Production 권장 | 필수 |
|---|---|---|---|---|
| platform CNPG | 인스턴스당 1 | **50Gi** ×3 (postgres-ha.yaml) | 네트워크 SC 지정(파일의 storageClass 주석 해제 — bootstrap 치환 대상 아님, 수동) | 필수 |
| auth CNPG | 인스턴스당 1 | **20Gi** ×3 (auth-db-ha.yaml) | 동일 | 필수 |
| Prometheus | kps 기본 | 기본값(보존 기본 10d) | 보존 기간·용량 정책 결정 후 values 지정 | 필수(관측) |
| Loki | 20Gi 단일 | loki-values.yaml (주석: 실서버는 오브젝트 스토리지 권장) | 오브젝트 스토리지 백엔드 또는 증설 | 권장 |
| Tempo | 로컬 블록 | tempo-values.yaml | 동일 계열 | 권장 |
| Gitea(선택) | helm persistence | bootstrap STORAGE_CLASS 전달됨 | 내부 저장소 사용 시 필수 | 선택 |
| Redis | 없음(비영속 캐시) | emptyDir 성격 | 영속화 불필요(설계) | — |
| MinIO | staging 대체물 | **운영 반입 금지** — 실제 오브젝트 스토리지 사용(§9) | — | — |

주의: staging 은 축소 용량(2Gi/1Gi)으로 돌았다 — **staging 값을 운영에 복사하지 말 것.**

## 6. Network / LoadBalancer 요구사항

- CNI: **NetworkPolicy 를 실제 강제**하는 CNI 필수(검증 기준 Calico). kindnet류(미강제) 불가 —
  테넌트 격리(default-deny)가 보안 모델의 핵심.
- **L4 LoadBalancer**: ingress-nginx Service(type LoadBalancer) 앞단. 클라우드 LB 또는
  bare-metal 구현(예: MetalLB, kube-vip — 선택지이며 강제 아님).
- ingress-nginx 설정(repo values 기준): replica 2 + 노드 분산(topologySpread),
  **`externalTrafficPolicy: Local`** — 클라이언트 IP 보존을 위해 LB 가
  **healthCheckNodePort 헬스체크로 컨트롤러 없는 노드를 제외**하도록 구성해야 한다
  (IP 보존은 엣지 rate-limit·LoginGuard 의 전제).
- WAF: ModSecurity + OWASP CRS 가 values 로 활성화됨(재설치 시 동일 values 사용).
- 고정(또는 운영 가능한) external IP 1개 이상 + 아래 DNS 연결.

## 7. DNS / TLS 요구사항

경로 기반 라우팅이라 **와일드카드는 필수가 아니다**. 실제 도메인 이름은 담당자가 정한다
(아래는 형식 — 가짜 도메인 생성 금지):

| 용도 | 레코드 형식 | 대상 | 비고 |
|---|---|---|---|
| 플랫폼(frontend `/` + API `/api` + auth `/api/auth` + 테넌트 `/svc/<slug>`) | A 또는 CNAME × 1 | L4 LB IP/호스트 | **단일 호스트 공유**(bootstrap DOMAIN 치환, 현재 placeholder `edu.internal`) |
| Gitea(내부 저장소, 선택) | A/CNAME × 1 | 동일 LB | `gitea.<도메인>` 형태 |
| Registry(폐쇄망 미러 사용 시) | 내부 DNS | 사내 레지스트리 | §10 |

TLS(cert-manager) — repo 의 두 가지 경로 중 담당자가 결정:
- **공인 도메인 + Let's Encrypt(ACME)**: ClusterIssuer 를 ACME 로 교체(HTTP-01 이면 80 포트
  인바운드 필요, 와일드카드 필요 시 DNS-01 + DNS 제공자 API 권한). PRODUCTION.md §3 절차.
- **사설 CA**: repo 기본 체인(selfsigned→root CA→`edu-ca` ClusterIssuer) 사용 — 기관 배포
  신뢰 저장소에 루트 CA 등록 필요.
- 테넌트 Ingress 는 ingress-shim 주석(`cert-manager.io/cluster-issuer: edu-ca`)으로 서비스별
  자동 발급(검증됨). 플랫폼 Ingress 는 현재 TLS 블록 없음(엣지/LB 종단 전제) — **TLS 종단
  지점(LB vs ingress)을 담당자가 결정**하고 개발팀에 알려줄 것.

## 8. 필수 Operator / 구성요소

| Component | 목적 | 필수 시점 | repo 자동 설치 | 확인 방법 |
|---|---|---|---|---|
| CloudNativePG | DB HA·백업·PITR | **A. 앱 배포 전** | 아니오(수동 helm/manifest — PRODUCTION.md §4) | `kubectl get crd clusters.postgresql.cnpg.io` |
| Sealed Secrets 컨트롤러 | Secret 반입 표준 | **A** | 아니오(수동 — secrets/README.md) | `kubeseal --fetch-cert` 성공 |
| ingress-nginx(+WAF values) | 엣지·rate-limit | **A** | `bootstrap.sh stack` 포함 | 컨트롤러 Ready + LB external IP |
| cert-manager(+발급자) | TLS 자동화 | **A** | stack 포함(발급자 apply 포함) | ClusterIssuer Ready |
| KEDA | 워커 큐 오토스케일 | **A**(ScaledObject 적용 전) | stack 포함 | `kubectl get crd scaledobjects.keda.sh` |
| kube-prometheus-stack | 메트릭·경보(Alertmanager 포함) | **B. 배포 직후**(단, 수신처 검증은 트래픽 전 필수) | stack 포함(AM on·CNPG PodMonitor 수집 플래그 반영됨) | 타깃 up, Edu 규칙 13종 로드 |
| Loki + Promtail | 로그 | B | stack 포함 | LogQL 조회 |
| Tempo | 트레이스 | B | stack 포함 | 데이터소스 로드 |
| metrics-server | HPA | **A** | 매니지드는 보통 내장 | `kubectl top nodes` |
| Gitea(선택) | 내부 코드 저장소 | 선택 | stack 포함 | 내부망 정책에 따라 |

## 9. Object Storage (DB 백업/PITR — 필수)

S3 API 호환이면 됨(S3·MinIO 운영 인스턴스·기타 호환 스토리지).
**staging 의 `minio.minio.svc:9000` 은 검증용 대체물 — 운영 반입 금지.**

| 항목 | 요구 |
|---|---|
| endpoint | 운영 오브젝트 스토리지 URL(가능하면 TLS) — `postgres-ha.yaml`·`auth-db-ha.yaml` 의 `endpointURL` 교체 |
| bucket | `edu-db-backups` (경로 `platform/`·`auth/` — 매니페스트 기준, 변경 시 매니페스트 수정) |
| region | 스토리지 요구 시 지정 |
| credential | `edu-db-backup-creds` Secret(키: `ACCESS_KEY_ID`, `ACCESS_SECRET_KEY`) — 쓰기 권한 |
| retention | 매니페스트 30d(barman retentionPolicy) — 정책 확정 |
| WAL archive | 자동(gzip) — 용량: WAL 연속 적재 감안해 수십 GiB 여유 |

## 10. Container Registry

- 이미지는 **GHCR 공개 레포**(`ghcr.io/05solar/edu-msa/…`) — 익명 pull 검증됨.
  클러스터 노드가 `ghcr.io` 로 egress 가능하면 **imagePullSecret 불필요**.
- **폐쇄망(인터넷 차단) 환경이면**: 내부 레지스트리 미러 필수 —
  ① 3종 이미지를 digest 기준으로 미러링 ② bootstrap `REGISTRY=<내부주소>` 로 치환
  ③ Kaniko 빌드 push 대상(`EDU_DEPLOY_REGISTRY`)도 내부 레지스트리로 지정(노드가 pull
  가능해야 함 — PRODUCTION.md §2, registry/README 패턴).

## 11. Production Secret 목록 (값 금지 — 이름/key 만)

반입 방식: **Sealed Secrets**(secrets/README.md — 평문 yaml 은 .gitignore 로 차단, 봉인본만
커밋 가능). bootstrap server 모드는 아래 ①~④ 부재 시 **기동 거부(fail-closed)**.

| # | Secret (namespace) | Key | 사용 서비스 | 필수 |
|---|---|---|---|---|
| ① | `edu-db` (edu-platform) | POSTGRES_DB / POSTGRES_USER / POSTGRES_PASSWORD | bootstrap 게이트(단일 DB 경로) | 필수 |
| ② | `edu-auth-db` (edu-platform) | 동일 3키 | 동일 | 필수 |
| ③ | `edu-auth-jwt` (edu-platform) | EDU_JWT_SECRET(≥32B) / EDU_SEED_PASSWORD | auth·backend·worker 공유 | 필수 |
| ④ | `edu-redis-auth` (edu-platform) | password | redis·auth·backend·worker | 필수 |
| ⑤ | `edu-db-backup-creds` (edu-platform) | ACCESS_KEY_ID / ACCESS_SECRET_KEY | CNPG 백업(§9) | 필수(HA) |
| ⑥ | `edu-alert-receiver` (**monitoring**) | `webhook-url` | Alertmanager(§12) | 필수 |
| ⑦ | `edu-gitea-token` (edu-platform) | username / token | backend·worker (optional 참조) | Gitea 사용 시 |
| ⑧ | `edu-gitea-webhook` (edu-platform) | secret | backend (optional) | Gitea 사용 시 |

참고: HA(CNPG) 경로의 앱 계정 Secret(`edu-db-app`/`edu-auth-db-app`)은 **오퍼레이터가 자동
생성** — 담당자가 만들지 않는다.

## 12. Alertmanager 운영 수신처 (BLOCKER ②)

- 현재 상태: 수신 **경로/라우팅은 구성·검증 완료**(rule→firing→AM→route→delivery 전 구간,
  staging). 단 전달 대상이 테스트 sink(`alert-sink.monitoring.svc`)다 — **운영 인정 불가**.
- 담당자가 할 일: 기관에서 실제 쓰는 알림 채널의 **webhook endpoint** 를 정해 Secret 으로 반입.
  가능 유형(사람이 선택): Slack incoming webhook · Mattermost · Discord · Teams ·
  자체 notification API(HTTP POST 수신 가능하면 됨 — Alertmanager webhook JSON 수신).
- Secret 구조(repo 기준 확인됨): name `edu-alert-receiver`, namespace `monitoring`,
  key **`webhook-url`** (alertmanager-values.yaml 의 url_file 마운트 경로와 일치).
- 반입 후 bootstrap(또는 helm upgrade -f alertmanager-values.yaml) 재실행 시 자동 배선.

## 13. 운영 수신처 검증 절차 (반입 후 즉시 — 명령 수준)

```bash
# 0) 전제: Secret 반입 + helm 재적용(bootstrap stack 또는 수동 upgrade) 후 AM 재기동 확인
kubectl -n monitoring get secret edu-alert-receiver           # 존재만 확인(값 출력 금지)
kubectl -n monitoring get pods -l app.kubernetes.io/name=alertmanager

# 1) 합성 발화 규칙 적용 (vector(1), for: 0m — 검증 후 반드시 삭제)
cat <<'EOF' | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata: { name: edu-receiver-check, namespace: monitoring, labels: { release: monitoring } }
spec:
  groups: [{ name: edu-receiver-check, rules: [{ alert: EduReceiverCheck,
    expr: vector(1) > 0, labels: { severity: info },
    annotations: { summary: "운영 수신처 전달 검증(합성)" } }] }]
EOF

# 2) firing 확인 (~30s 내) — 시각 기록
kubectl -n monitoring exec sts/prometheus-monitoring-kube-prometheus-prometheus -c prometheus -- \
  wget -qO- 'http://localhost:9090/api/v1/query?query=ALERTS{alertname="EduReceiverCheck"}'

# 3) AM 수신·라우팅 확인 — 시각 기록
kubectl -n monitoring port-forward svc/monitoring-kube-prometheus-alertmanager 9093 &
curl -s http://localhost:9093/api/v2/alerts | grep EduReceiverCheck   # 수신
curl -s http://localhost:9093/api/v2/status | grep edu-webhook        # 라우트 로드

# 4) **사람이 실제 채널(Slack 등)에서 메시지 수신을 눈으로 확인** — 수신 시각 기록
#    → firing→최종 수신 지연(group_wait 30s 포함, 통상 1~2분 내)을 기록해야 PASS

# 5) 정리
kubectl -n monitoring delete prometheusrule edu-receiver-check
```

기록 항목: firing 시각 / AM 수신 시각 / route 매칭 / **실채널 수신 확인(사람)** / 전달 지연.
실채널 수신 확인 없이는 PASS 아님.

## 14. 준비 체크리스트 (인프라/운영 담당자)

- [ ] Production K8s 클러스터(§3 — 워커 3+·HA CP 또는 매니지드)
- [ ] 노드 리소스(§4 합산 기준) 확보
- [ ] **분산/네트워크 StorageClass**(§5) — local-path 아님을 확인
- [ ] NetworkPolicy 강제 CNI(§6)
- [ ] L4 LoadBalancer + external IP(§6) — externalTrafficPolicy Local 헬스체크 구성
- [ ] DNS 레코드(§7) — 플랫폼 호스트(+Gitea 선택)
- [ ] TLS 경로 결정·준비(§7) — ACME 또는 사설 CA, 종단 지점 결정
- [ ] Operator 설치(§8 A 그룹): CNPG · Sealed Secrets · ingress-nginx · cert-manager · KEDA · metrics-server
- [ ] Object Storage(§9) — bucket·credential·retention
- [ ] Registry pull 경로(§10) — GHCR egress 또는 내부 미러
- [ ] Production Secret 봉인 반입(§11 ①~⑥, Gitea 사용 시 ⑦⑧)
- [ ] **운영 Alertmanager 수신처**(§12) 반입 + §13 검증 1회 완료
- [ ] kubeconfig/접근 정보 전달(§15) — 최소 권한(아래 RBAC)

### RBAC (배포 계정 최소 권한 — 이번 단계에서 생성하지 않음, 제안만)

cluster-admin 불요. 제안 구조: 배포용 ServiceAccount + 아래 스코프의 Role/ClusterRole 바인딩.

| 스코프 | 리소스 | 동사 |
|---|---|---|
| ns `edu-platform`·`edu-services`·`edu-services-public`·`monitoring` (Role) | deployments, services, ingresses, secrets, configmaps, hpa, pdb, serviceaccounts, jobs, pods(+log/exec는 운영 판단) | get/list/watch/create/patch/update/delete |
| 동일 ns (Role) | `postgresql.cnpg.io/*`(clusters, poolers, backups, scheduledbackups), `keda.sh/scaledobjects`, `monitoring.coreos.com/*`(servicemonitors, podmonitors, prometheusrules), `bitnami.com/sealedsecrets`, networkpolicies, resourcequotas, limitranges, pvc | 동일 |
| 클러스터 (ClusterRole, 읽기 위주) | namespaces(create 포함 — 최초 1회), nodes(get/list — 사전점검), crd(get/list), storageclasses(get/list) | 제한적 |
| 참고 | 테넌트 배포 런타임 권한은 repo 의 `edu-deployer` SA(rbac.yaml)가 이미 정의 — 배포 계정과 별개 | — |

### Namespace 계획 (repo 기준)

| Namespace | 역할 | PSA | NetworkPolicy | ResourceQuota |
|---|---|---|---|---|
| `edu-platform` | 플랫폼 코어(backend·auth·worker·frontend·DB·Redis) | baseline | 없음(신뢰 tier) | 없음 |
| `edu-services` | 내부 직원 테넌트 | baseline | default-deny + DNS/ingress 허용 + 인터넷(사설망 제외) | cpu 8/16, mem 16/32Gi, pods 100 |
| `edu-services-public` | 외부/비신뢰 테넌트 | **restricted**(+gVisor 옵션) | default-deny + DNS/ingress 만 | cpu 4/8, mem 8/16Gi, pods 200 |
| `monitoring` | kps·AM·(Loki/Tempo) | — | — | — |
| `cnpg-system`·`keda`·`ingress-nginx`·`cert-manager` | 오퍼레이터 | — | — | — |
| `gitea` (선택) | 내부 저장소 | — | — | — |

## 15. 준비 완료 후 개발팀에 전달할 정보

| 항목 | 값/상태 |
|---|---|
| Production kube context (kubeconfig) | 사람이 제공 |
| Kubernetes API endpoint | 사람이 제공 |
| StorageClass 이름 | 사람이 제공 (bootstrap `STORAGE_CLASS=` 로 주입됨) |
| LoadBalancer IP/hostname | 사람이 제공 |
| 플랫폼 도메인 (bootstrap `DOMAIN=`) | 사람이 제공 |
| TLS 방식(ACME/사설 CA)·종단 지점 | 사람이 결정·통보 |
| Object storage endpoint / bucket | 사람이 제공 (매니페스트 endpointURL 교체용) |
| Registry (GHCR 직접 or 내부 미러 주소) | 사람이 제공 (bootstrap `REGISTRY=`) |
| Sealed Secrets 반입 완료 여부(§11) | 사람이 확인 |
| Alert receiver 반입·§13 검증 완료 여부 | 사람이 확인 |
| DNS 반영 완료 여부 | 사람이 확인 |
| 배포 계정 RBAC 적용 여부 | 사람이 확인 |

## 16. 재개 게이트 (전부 충족 시에만 배포 프롬프트 재실행)

1. 실제 Production kube context 제공 2. 워커 3+ 확인 3. 분산 StorageClass 확인
4. L4 LB 확인 5. DNS 확인 6. TLS 발급 가능 7. **운영 수신처 §13 검증 통과**
8. object storage 확인 9. registry pull 확인 10. §11 Secret 전부 반입.
하나라도 미충족이면 배포하지 않는다. 배포 절차 자체는
[PRODUCTION_READINESS.md](PRODUCTION_READINESS.md) §11-5 런북과 `IMAGE_TAG=git-2737bdc` 를 그대로 사용한다.

---

## 갱신 이력
- 2026-09-10 — 최초 작성(BLOCKED 게이트 확정 직후, repo 사실 기준 전수 추출).
