# ROADMAP.md · 대규모 전환 로드맵

현재 구조는 소규모엔 적합하나 대규모(수십만 사용자·수천 서비스)엔 아래 축이 비어 있다.
평가 근거는 [SECURITY.md](SECURITY.md)와 검증 기록(PROCESS.md) 참고. 각 항목은
**구현 → 로컬(kind) 테스트/검증 → 문서화** 순으로 순차 진행한다.

## P0 (대규모 필수 · 없으면 못 버팀)

- [x] **P0-1 오토스케일** — HPA + PodDisruptionBudget (완료·검증)
  - 구현: `deploy/k8s/platform/autoscale.yaml`(backend/frontend HPA+PDB), 서비스 템플릿에 HPA 추가(동적 서비스 자동확장).
  - 검증(kind): metrics-server 설치, workdays HPA가 `cpu: 1%/70%` 메트릭 정상 판독, PDB 활성. (Cluster Autoscaler는 실서버 노드풀 필요)
- [x] **P0-2 배포 오케스트레이션 분리** — DB 작업 큐 + 워커 (완료·검증)
  - 구현: `DeployJob` 엔티티/큐, `DeployJobService`(claimNext = `FOR UPDATE SKIP LOCKED` 행잠금),
    `DeployWorker`(@Scheduled 폴링, 재시도), 승인/등록은 큐에 적재(비동기 202). @EnableScheduling.
  - 검증: 적재→워커 처리→`done`(deploymentId 부여·컨테이너 기동), 실패 소스는 attempts 0→1→2 후 `failed`.
- [x] **P0-3 데이터베이스 HA** — CloudNativePG 3-인스턴스 클러스터 (완료·검증)
  - 구현: `deploy/k8s/platform/postgres-ha.yaml`(Cluster instances 3, rw/ro 서비스), backend.yaml을 `edu-db-rw` + `edu-db-app` 시크릿으로 전환.
  - 검증(kind): 3/3 healthy(primary+replica 2), **primary 파드 삭제 → 복제본 자동 승격(edu-db-1→edu-db-2) → 3/3 재수렴**.

## P1 (프로덕션 안전)

- [x] **P1-1 안전 빌드 + 신뢰도별 네임스페이스 자동배치** (완료)
  - [x] 네임스페이스 자동배치 — CODER/ADMIN→`edu-services`, USER/익명/불명→`edu-services-public`(fail-closed, 서브에이전트 PASS). 검증: 내부/외부 소유자 매니페스트 namespace 분기 확인.
  - [x] Kaniko 인클러스터 빌드(docker.sock 제거) — real 모드가 host `docker build` 대신 Kaniko Job(kubectl apply + wait)으로 이미지 빌드. 미신뢰 업로더 입력(repoUrl/branch) 정규식 검증으로 인자 주입 차단.
    검증: kind에서 Kaniko Job이 실제 GitHub 레포(test-code, Go)를 docker.sock 없이 빌드→인클러스터 레지스트리 push 성공(카탈로그 `{"repositories":["workdays"]}`), 서브에이전트 리뷰 PASS(보안 지적 반영).
    남은 인프라: 노드가 pull 가능한 레지스트리 엔드포인트(kind-local-registry 패턴 또는 사내 레지스트리) 구성 — 문서화.
- [x] **P1-2 NetworkPolicy 강제** — Calico 도입 (완료·검증)
  - 구현: kind를 `disableDefaultCNI`로 재생성 + Calico v3.28 설치, hardening NetworkPolicy 적용.
  - 검증: 공개 tier 외부 egress **차단**(DNS만 허용), 내부 tier 인터넷 **허용**, 정책 없는 ns는 개방 → 정책이 실제 강제됨. PodSecurity baseline/restricted 확인.
- [x] **P1-3 가용성** — 테넌트 서비스 템플릿에 무중단 롤링(maxUnavailable:0/maxSurge:1), PDB(maxUnavailable:1), AZ 분산(topologySpread ScheduleAnyway)+노드 안티어피니티(soft) 추가.
  검증(kind): 2-replica 앱에 롤링 업데이트 2회 중 220요청 실패 1(99.5%), 롤아웃 내내 2 Ready 유지. PDB ALLOWED DISRUPTIONS=1. 서브에이전트 PASS.
  주의: maxUnavailable:0는 서지(+1) 파드가 필요 → 네임스페이스 ResourceQuota가 N+1 허용해야 롤아웃 정체 없음.

## P2 (운영 성숙도)

- [x] **P2-1 유휴 비용** — scale-to-zero (KEDA HTTP add-on). 클러스터에 KEDA+http-add-on 설치, HTTPScaledObject 템플릿(`deploy/k8s/scale-to-zero-template.yaml`) + 문서(`deploy/k8s/platform/scale-to-zero/README.md`).
  검증(kind): 유휴 서비스 `replicas=0` 도달 → 인터셉터 경유 요청 시 0→1 콜드스타트, HTTP 200(2.85s), ready=1.
  적용 시 주의: Ingress 백엔드를 KEDA 인터셉터로 지정(경유 필수), 저지연 필요 서비스는 min:1.
- [x] **P2-2 관측성** — kube-prometheus-stack(Prometheus+Grafana+kube-state-metrics+node-exporter). 백엔드 `/actuator/prometheus` 노출(micrometer) + ServiceMonitor(`deploy/k8s/platform/monitoring/`).
  검증(kind): `up` 11타깃 스크레이프, 백엔드 메트릭 200(application 라벨), ServiceMonitor 디스커버리→`up{job="promex"}=1`(~30s), 서브에이전트 PASS.
  남음: Loki/Tempo(로그·트레이스), Alertmanager/규칙, 대시보드 코드화.
- [x] **P2-3 엣지** — ingress-nginx(ModSecurity+OWASP CRS) TLS·rate-limit·WAF. 엣지 자산(`deploy/k8s/platform/edge/`) + 테넌트 템플릿 Ingress에 rate-limit/WAF 주석.
  검증(kind): TLS 200(우리 인증서), WAF 차단 XSS/SQLi/traversal 403, 정상 200, rate-limit 503. 서브에이전트 PASS.
  남음: cert-manager 자동 인증서, CRS 튜닝, CDN/DDoS.

## P3 (프로덕션 마감)

- [x] **P3-1 자동 TLS 인증서** — cert-manager. 발급자 체인(selfsigned→루트 CA→`edu-ca`, `deploy/k8s/platform/edge/cert-manager/`) + 테넌트 템플릿 Ingress에 `cert-manager.io/cluster-issuer` + `spec.tls` 자동 배선.
  검증(kind): 발급자 Ready, 명시적 Certificate 자동 발급(issuer=CN=edu-msa-root-ca), **ingress-shim 주석만으로 ~3초 내 인증서+시크릿 자동 생성**(우리 CA 서명, SAN 일치). 서브에이전트 PASS.
  실서버: `edu-ca` → ACME(Let's Encrypt)로 교체. 최적화: 공유 호스트는 서비스별 중복 대신 단일/와일드카드 인증서 검토.
- [~] **P3-2 로그·트레이스** — 로그(Loki)·트레이스(Tempo) 완료. (감사로그는 남음)
  - [x] Loki + Promtail(`deploy/k8s/platform/logging/`) + Grafana Loki 데이터소스.
    검증(kind): LogQL 조회 5줄 매칭, 라벨 추출, 사이드카 로드. 서브에이전트 PASS.
  - [x] Tempo(`deploy/k8s/platform/tracing/`) + 트레이스↔로그 상관(tracesToLogsV2 / derivedFields).
    검증(kind): telemetrygen 20 트레이스 전송→Tempo 검색 20건 조회(rootServiceName 확인), Grafana Tempo 데이터소스 로드. 서브에이전트 PASS(로그→트레이스 url `$$` 이스케이프 결함 수정).
  - [ ] 감사로그 파이프라인, 앱 실제 OTel 계측, trace_id 로그 표준화
- [x] **P3-3 알림** — Alertmanager 활성화 + PrometheusRule(`deploy/k8s/platform/monitoring/prometheus-rules.yaml`: BackendDown/PodCrashLooping/HighMemory).
  검증(kind): 규칙 로드, 테스트 알림 Prometheus **firing** → Alertmanager **active** 수신 확인(파이프라인). 서브에이전트 PASS.
  남음: 수신처(Slack/Email) 라우팅, 억제/그룹핑, SLO 규칙 확장.
- [x] **P3-4 레지스트리 pull 경로** — kind-local-registry(containerd certs.d) 자산(`deploy/k8s/platform/registry/`).
  검증(kind, end-to-end): Kaniko가 test-code(Go)를 docker.sock 없이 빌드→`kind-registry:5000/workdays:v1` push(digest a3c56d8…) → `localhost:5001/workdays:v1` 배포(imagePullPolicy: Always) → kubelet **레지스트리 pull(221ms), Image ID digest 일치**, 파드 1/1 Running, 서비스 `/`·`/healthz` 200. 서브에이전트 PASS.
  즉 **GitHub 레포 → 인클러스터 빌드 → 레지스트리 → 노드 pull → 서비스 기동** 전 구간 완결.

## 진행 이력
- 2026-08-25 — 로드맵 작성. P0-1(오토스케일) 착수.
- 2026-08-25 — P0-1 완료·검증(HPA `cpu:1%/70%` 판독, PDB).
- 2026-08-25 — P0-2 완료·검증(작업 큐+워커, done/재시도→failed 확인).
- 2026-08-25 — P0-3 완료·검증(CloudNativePG 3-인스턴스, primary 삭제→자동 승격 확인). **P0 전부 완료.**
- 2026-08-25 — P1-2 완료·검증(Calico + NetworkPolicy 강제).
- 2026-08-25 — P1-1 진행: 신뢰도별 네임스페이스 자동배치 구현·검증(내부→edu-services, 외부→edu-services-public, fail-closed, 서브에이전트 PASS).
- 2026-08-25 — P1-1 완료: Kaniko 인클러스터 빌드(docker.sock 제거) 배선 + repoUrl/branch 주입 검증. kind에서 실제 GitHub 레포 빌드→레지스트리 push 검증, 서브에이전트 PASS.
- 2026-08-25 — P1-3 완료: 서비스 템플릿에 무중단 롤링+PDB+AZ분산/안티어피니티. kind에서 롤링 무중단(220/220-1) + PDB 검증, 서브에이전트 PASS.
- 2026-08-25 — P2-1 완료: scale-to-zero(KEDA HTTP add-on). kind에서 유휴 0축소→요청 시 0→1 콜드스타트(HTTP 200) 검증.
- 2026-08-25 — P2-2 완료: 관측성(kube-prometheus-stack). 백엔드 Prometheus 메트릭 노출 + ServiceMonitor. kind에서 스크레이프·디스커버리 검증, 서브에이전트 PASS.
- 2026-08-25 — P2-3 완료: 엣지(ingress-nginx + ModSecurity/OWASP CRS). kind에서 TLS·WAF 차단(XSS/SQLi/traversal 403)·rate-limit(503) 검증, 서브에이전트 PASS. **P0·P1·P2 로드맵 전체 완료.**
- 2026-08-25 — P3-1 완료: cert-manager 자동 TLS. 발급자 체인 + ingress-shim(주석만으로 인증서 자동 발급) kind 검증, 서브에이전트 PASS.
- 2026-08-25 — P3-2 진행: Loki + Promtail 로그 수집 + Grafana 연동. kind에서 LogQL 조회(5줄 매칭)·데이터소스 로드 검증, 서브에이전트 PASS. 남음: Tempo/감사로그.
- 2026-08-25 — P3-3 완료: 알림(Alertmanager + PrometheusRule). kind에서 규칙 로드·발화→Alertmanager active 수신 검증, 서브에이전트 PASS.
- 2026-08-25 — P3-2 완료(로그+트레이스): Tempo 설치 + 트레이스↔로그 상관. kind에서 트레이스 20건 전송·조회 검증, 서브에이전트 PASS(url $$ 이스케이프 결함 수정). 관측성 3축(metrics/logs/traces) 완성.
- 2026-08-25 — P3-4 완료: 레지스트리 pull 경로(kind-local-registry, containerd certs.d). 클러스터 재생성 후 Kaniko 빌드→push→노드 pull(digest 일치)→서비스 200 end-to-end 검증, 서브에이전트 PASS. **로드맵 실구현 항목 전부 완료(P0~P3).** 잔여는 감사로그·실 OTel 계측 등 문서화 항목.
