# INFRA.md · 인프라 세팅 개요 (deploy/)

교육청 내부 플랫폼: 직원/사용자가 바이브코딩한 프로그램을 GitHub에 올리면, 플랫폼이 레포를
가져와 **격리된 MSA 서비스(컨테이너/파드)**로 배포하고 웹에서 바로 쓰게 한다. 본 문서는
`deploy/` 아래 인프라 구성 전체를 한 곳에 설명한다. 세부는 각 컴포넌트 README 참조.

---

## 0. 신뢰 모델 (모든 설계의 전제)

| 대상 | 신뢰도 | 배포 네임스페이스 | 격리 강도 |
|---|---|---|---|
| 내부 직원(CODER/ADMIN) | 반신뢰 | `edu-services` | PodSecurity **baseline** 강제 |
| 불특정 다수(USER/익명/불명) | 비신뢰 | `edu-services-public` | PodSecurity **restricted** 강제 |

배포 네임스페이스는 업로더 신뢰도로 **자동 결정**되며 애매하면 무조건 비신뢰(fail-closed).

---

## 1. 디렉터리 지도

```
deploy/
├─ docker-compose.yml            # 로컬 개발(backend + postgres, docker 모드)
├─ INFRA.md · PROCESS.md · AGENT.md
└─ k8s/
   ├─ namespaces.yaml            # edu-platform, edu-services
   ├─ service-template.yaml      # 테넌트 서비스 1개당 렌더 대상(Deploy/Svc/Ingress/HPA/PDB)
   ├─ scale-to-zero-template.yaml# HTTPScaledObject 템플릿(KEDA)
   ├─ hardening/                 # 멀티테넌트 보안
   │  ├─ 00-namespaces-tiers.yaml   # 신뢰등급별 ns + PodSecurity
   │  ├─ 10-resourcequota-limits.yaml
   │  ├─ 20-networkpolicy.yaml      # default-deny + tier별 egress
   │  ├─ 30-runtimeclass-gvisor.yaml
   │  └─ 40-kaniko-build-job.template.yaml
   └─ platform/
      ├─ backend.yaml, frontend.yaml, ingress.yaml
      ├─ postgres.yaml(개발) / postgres-ha.yaml(CloudNativePG)
      ├─ rbac.yaml               # edu-deployer(최소권한)
      ├─ autoscale.yaml          # HPA + PDB(플랫폼)
      ├─ registry/               # (P3-4) kind-local-registry
      ├─ scale-to-zero/          # (P2-1) KEDA
      ├─ monitoring/             # (P2-2/P3-3) Prometheus/Grafana/알림
      ├─ logging/                # (P3-2) Loki
      ├─ tracing/                # (P3-2) Tempo
      └─ edge/                   # (P2-3/P3-1) ingress-nginx WAF + cert-manager
```

---

## 2. 플랫폼 코어

- **backend**(`platform/backend.yaml`): 2복제, `serviceAccountName: edu-deployer`(최소권한 RBAC),
  `EDU_DEPLOY_MODE=real`. DB는 `edu-db-rw`(CloudNativePG primary 라우팅) + `edu-db-app` 시크릿.
  메트릭 `/actuator/prometheus` 노출.
- **frontend**(`platform/frontend.yaml`): React 정적 서빙.
- **DB**: `postgres-ha.yaml`(CloudNativePG Cluster 3-인스턴스, 자동 장애조치) / 개발은 `postgres.yaml`.
- **ingress**(`platform/ingress.yaml`): 플랫폼 UI/API 진입.
- **RBAC**(`platform/rbac.yaml`): `edu-deployer` SA가 `edu-services`에 Deploy/Svc/Ingress만 CRUD(파드는 read).

---

## 3. 배포 파이프라인 (GitHub 레포 → 실행 서비스)

백엔드 `deploy` 도메인이 수행하는 순서:

```
GitHub 레포 URL
  → SourceResolver(clone)  → SpecParser(service.yaml)  → ServiceSpecValidator(슬러그/포트/헬스/중복)
  → 신뢰도별 네임스페이스 결정(resolveNamespace, fail-closed)
  → [real 모드] Kaniko 인클러스터 빌드(docker.sock 미사용) → 레지스트리 push
  → ManifestRenderer(service-template.yaml 치환) → kubectl apply -n <ns>
  → HPA/PDB/NetworkPolicy/PodSecurity 자동 적용 → 서비스 공개
```

- **빌드(P1-1)**: Kaniko Job(`kaniko-job.yaml`). 미신뢰 입력(repoUrl/branch)은 정규식 검증 후 삽입.
- **레지스트리 pull(P3-4)**: 노드 containerd가 `localhost:5001`을 `kind-registry:5000`으로 해석해 pull.
- **테넌트 매니페스트**(`service-template.yaml`)에 포함: 강화 securityContext(runAsNonRoot/캡드롭/seccomp),
  무중단 롤링(maxUnavailable:0), PDB, topologySpread/anti-affinity, HPA, rate-limit/WAF 주석, 자동 TLS.

---

## 3-1. 기본 내장 서비스 (7개 · 카테고리별 1개 · 개인용 단발 도구)

플랫폼에 동봉되는 **개인용 단발 도구 7개**(`examples/`). 교육청 전 직원이 필요할 때 접속해
한 번의 작업(검사·변환·생성·계산·추출)을 처리하고 끝내는 도구이며, 여러 사용자가 상호작용하며
상태를 관리·보고하는 협업 시스템이 아니다. 7개 업무 분야(category)에 각각 하나씩 존재한다.
seed(`programs.json`)에 **내부 계정 소유**로 등록되고 소스는 `local://examples/<slug>`.

| category | slug | 서비스 | 언어 |
|---|---|---|---|
| doc 문서·공문 | doc-proofreader | 공문서 오타·맞춤법 검사기 | Go |
| student 학생·성적 | seat-maker | 학생 자리배치 생성기 | Python |
| curri 교육과정 | timetable-checker | 시간표 충돌 검사·이미지 생성기 | TypeScript |
| budget 예산·회계 | travel-allowance | 국내출장 여비 계산기 | C# |
| facil 시설·안전 | asset-label | 비품 QR 라벨 시트 생성기 | Java |
| data 데이터 | data-summarizer | 표 데이터 통계 요약·차트 생성기 | Python |
| civil 민원 | doc-ocr | 문서 이미지 OCR 추출기 | Python |

모두 비루트·`/healthz`·개인 단발형(상태 저장·공유 없음). 검증: docker 모드 배포 시 `edu-svc-<slug>`
컨테이너 기동→`/healthz 200`→서브도메인 실접속. 상세는 [../examples/README.md](../examples/README.md).

### 서브도메인 라우팅 (Traefik)
"웹에서 바로 사용"은 포트가 아니라 **서브도메인** `http://<slug>.localhost` 로 열린다.
브라우저가 `*.localhost` 를 127.0.0.1로 처리 → 호스트 :80의 Traefik이 Host 헤더로 라우팅.
배포 시 백엔드(`DeploymentService`)가 컨테이너를 `eduproxy` 네트워크에 합류시키고 Traefik
파일 프로바이더 라우트(`/dynamic/<slug>.yml`, `<slug>.localhost → http://edu-svc-<slug>:<port>`)를
기록한다. 컨테이너 `/healthz` 응답까지 대기(readiness)한 뒤 `running`으로 표시해 첫 접속 502를 방지.

## 4. 멀티테넌트 보안 (hardening/)

| 파일 | 통제 |
|---|---|
| `00-namespaces-tiers` | 신뢰등급별 ns + **PodSecurity Admission**(baseline/restricted) |
| `10-resourcequota-limits` | ns별 ResourceQuota/LimitRange(자원 독점 방지) |
| `20-networkpolicy` | **default-deny** + DNS 허용 + tier별 egress(공개 tier는 사설망 차단) |
| `30-runtimeclass-gvisor` | 샌드박스 런타임(gVisor) RuntimeClass |
| `40-kaniko-build-job` | docker.sock 없는 안전 빌드 |

CNI는 **Calico**(NetworkPolicy 강제; kindnet은 미강제). 검증: 공개 tier egress 차단/내부 tier 허용 대조.

---

## 5. 운영 성숙도 스택 (platform/*)

| 영역 | 컴포넌트 | 자산 | 핵심 |
|---|---|---|---|
| 오토스케일 | HPA + PDB, metrics-server | `autoscale.yaml` | CPU 70% 타깃, 최소가용 보장 |
| 유휴 비용 | KEDA HTTP add-on | `scale-to-zero/` | 유휴 0 축소 → 요청 시 0→1 콜드스타트 |
| 메트릭 | kube-prometheus-stack | `monitoring/` | ServiceMonitor로 백엔드/서비스 스크레이프 |
| 알림 | Alertmanager + PrometheusRule | `monitoring/prometheus-rules.yaml` | BackendDown/CrashLoop/HighMemory |
| 로그 | Loki + Promtail | `logging/` | LogQL 조회, Grafana 연동 |
| 트레이스 | Tempo(OTLP) | `tracing/` | 트레이스↔로그 상관 |
| 엣지 | ingress-nginx + ModSecurity/OWASP CRS | `edge/` | TLS·rate-limit·**WAF(403)** |
| 인증서 | cert-manager | `edge/cert-manager/` | Ingress 주석만으로 자동 발급 |
| 레지스트리 | kind-local-registry | `registry/` | 노드 pull 경로 |

관측성 3축(metrics/logs/traces) + 알림이 Grafana 한 곳에서 상관 조회된다.

---

## 6. 요청 흐름 (사용자 → 서비스)

```
사용자 →(HTTPS)→ ingress-nginx [TLS 종료 · rate-limit · WAF(ModSecurity/CRS)]
   ├─ 플랫폼 UI/API → frontend / backend(edu-platform)
   └─ 배포된 서비스 → 신뢰도별 라우팅
        ├─ 내부(edu-services)         : baseline, 사설망 egress 허용
        └─ 공개(edu-services-public)  : restricted, 사설망 차단, (선택)gVisor, KEDA scale-to-zero
```

---

## 7. 브링업 순서 (실서버 기준)

1. 클러스터 + CNI(Calico) + (kind는 `registry/setup-local-registry.sh`).
2. `k8s/namespaces.yaml`, `hardening/*` 적용(신뢰등급/PSA/쿼터/NetworkPolicy).
3. `platform/rbac.yaml`, `postgres-ha.yaml`(CNPG), `backend.yaml`/`frontend.yaml`/`ingress.yaml`.
4. 운영 스택(helm): metrics-server → kube-prometheus-stack → KEDA → ingress-nginx →
   cert-manager → Loki → Tempo (각 README의 helm 명령).
5. `monitoring/`·`logging/`·`tracing/` 데이터소스/규칙/ServiceMonitor 적용.

> 상세 K8s 배포 가이드는 [k8s/README.md](k8s/README.md), 인프라 규칙은 [AGENT.md](AGENT.md),
> 진행/검증 이력은 [PROCESS.md](PROCESS.md), 로드맵/검증현황은 [../ROADMAP.md](../ROADMAP.md),
> 보안 하드닝은 [../SECURITY.md](../SECURITY.md) 참조.

---

## 8. 검증 현황 (kind에서 실검증 + 서브에이전트 교차검증)

P0(오토스케일·배포큐·DB HA) · P1(Kaniko·네임스페이스·NetworkPolicy·가용성) ·
P2(scale-to-zero·관측성·엣지WAF) · P3(자동TLS·로그·트레이스·알림·레지스트리 pull) — **전부 완료**.
대표 근거: CNPG 자동 승격, 무중단 롤링(220/219), WAF 403, scale-to-zero 0→1, Kaniko digest 일치 pull.
