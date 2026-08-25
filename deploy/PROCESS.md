# PROCESS.md · 인프라(deploy) 진행 이력

`deploy/`는 플랫폼과 배포 파이프라인의 인프라 산출물을 담는다.

```
deploy/
├── docker-compose.yml     # 로컬: postgres + backend (docker 실배포 모드 지원)
└── k8s/
    ├── namespaces.yaml            # 기본 네임스페이스
    ├── platform/                  # 플랫폼(postgres/backend/frontend/ingress/rbac)
    ├── service-template.yaml      # 서비스 1개당 렌더링 템플릿(보안 컨텍스트 포함)
    ├── hardening/                 # 멀티테넌트 보안 하드닝(PodSecurity/Quota/NetPol/RuntimeClass/Kaniko)
    └── README.md                  # K8s 배포 가이드
```

## 프로세스

1. 매니페스트는 **최소 권한·기본 차단** 원칙으로 작성한다.
2. 로컬 kind로 적용·검증 후, 실서버(Calico/Cilium, gVisor, 레지스트리)로 승격한다.
3. 변경 시 본 이력과 관련 문서(README/SECURITY/AGENT)를 갱신한다.

## 진행 이력 (Change Log)

- 2026-08-24 — docker-compose(postgres+backend) 작성, K8s 매니페스트(namespace·플랫폼·서비스 템플릿·RBAC) 추가.
- 2026-08-25 — docker 실배포 모드용 docker.sock/examples 마운트, 백엔드 이미지에 git/docker/kubectl.
- 2026-08-25 — 로컬 kind 리허설: Go 서비스가 Pod+Service로 기동·응답 검증. deploy/k8s/README를 K8s 배포 가이드로 리뉴얼.
- 2026-08-25 — P3-2 트레이싱: Tempo(모놀리식, OTLP 4317/4318) 설치, tracing/ 자산 + Grafana Tempo 데이터소스(tracesToLogsV2→loki) + Loki derivedFields(→tempo). kind 검증: telemetrygen 20 트레이스 전송→Tempo `/api/search` 20건 조회(rootServiceName=edu-trace-test), 사이드카 로드. 서브에이전트 PASS — 로그→트레이스 provisioning url `${...}`→`$${...}` 이스케이프 결함 수정. 관측성 3축 완성.
- 2026-08-25 — P3-3 알림: Alertmanager 활성화(helm upgrade), PrometheusRule(monitoring/prometheus-rules.yaml: EduBackendDown/PodCrashLooping/HighMemory, release: kps) + README 알림 섹션. kind 검증: 규칙 4종 로드, 테스트 알림 Prometheus firing→Alertmanager active 수신, 이후 테스트 알림 비활성 재적용(실규칙 3종). 서브에이전트 PASS(job 라벨/limit 미설정 주의 반영).
- 2026-08-25 — P3-2 로그 수집: loki-stack(Loki+Promtail) 설치(helm), logging/ 자산 + Grafana Loki 데이터소스 ConfigMap(사이드카 자동 로드) + README. kind 검증: 테스트 토큰 로그→LogQL `{namespace="default"} |= "..."` status=success 5줄 매칭, 라벨 정상, 사이드카 provisioning 로드 확인. 서브에이전트 PASS(loki-stack deprecated·검증 emptyDir 반영).
- 2026-08-25 — P3-1 자동 TLS: cert-manager 설치(helm, CRDs), 발급자 체인(selfsigned→edu-root-ca(isCA)→edu-ca) 자산 + README, 테넌트 서비스 템플릿 Ingress에 cert-manager.io/cluster-issuer + spec.tls 배선. kind 검증: 발급자 Ready, 명시적 Certificate 자동발급(issuer=CN=edu-msa-root-ca), ingress-shim 주석만으로 ~3초 내 인증서/시크릿 자동 생성(SAN 일치). 서브에이전트 PASS.
- 2026-08-25 — P2-3 엣지: ingress-nginx 설치(helm, values로 ModSecurity+OWASP CRS 전역 On, TLS1.2/1.3, replica 2). edge/ 자산 + README, 테넌트 서비스 템플릿 Ingress에 rate-limit/WAF 주석. kind 검증(컨트롤러 ClusterIP --resolve): TLS 200(CN=edu-waf.internal), WAF XSS/SQLi/traversal 403, 정상 200, rate-limit 503. 서브에이전트 PASS. P2 및 전체 로드맵 완료.
- 2026-08-25 — P2-2 관측성: kube-prometheus-stack 설치(helm, alertmanager off, retention 2h). backend Service 포트명/라벨 + ServiceMonitor(platform/monitoring/) 추가, README. kind 검증: up 11타깃 스크레이프, ServiceMonitor 디스커버리(예제앱 up=1, ~30s). 백엔드는 micrometer로 /actuator/prometheus 노출.
- 2026-08-25 — P2-1 scale-to-zero: KEDA + keda-add-ons-http 설치(helm), HTTPScaledObject 템플릿(scale-to-zero-template.yaml) + platform/scale-to-zero/README 추가. kind 검증: 샘플 서비스 유휴→replicas 0, 인터셉터 경유 요청 시 0→1 콜드스타트(HTTP 200, 2.85s). 라우팅은 인터셉터(keda-add-ons-http-interceptor-proxy:8080) 경유 필수.
- 2026-08-25 — P1-3 가용성: 서비스 템플릿에 무중단 롤링(maxUnavailable:0/maxSurge:1)+PDB(maxUnavailable:1)+topologySpread(AZ)/anti-affinity(soft). kind 검증: 롤링 중 무중단(220요청 실패 1), PDB ALLOWED=1.
- 2026-08-25 — P1-1 Kaniko 안전빌드: 백엔드 real 모드가 docker.sock 대신 Kaniko Job으로 빌드. kind 검증: 실제 GitHub 레포 빌드→인클러스터 레지스트리 push. repoUrl/branch 주입 검증 추가.
- 2026-08-25 — P1-2 NetworkPolicy 강제: kind를 disableDefaultCNI로 재생성 + Calico v3.28 설치. hardening NetworkPolicy 적용 후 검증 — 공개 tier 외부 egress 차단(DNS만), 내부 tier 인터넷 허용, 정책없는 ns 개방. (kindnet에선 미강제였음)
- 2026-08-25 — P0-3 DB HA: CloudNativePG 오퍼레이터 + postgres-ha.yaml(Cluster instances 3), backend.yaml을 edu-db-rw + edu-db-app 시크릿으로 전환. kind 검증: 3/3 healthy, primary 삭제→복제본 자동 승격(edu-db-1→2)→재수렴. P0 전부 완료.
- 2026-08-25 — 대규모 로드맵(ROADMAP.md) 착수. P0-1 오토스케일: platform/autoscale.yaml(HPA+PDB), 서비스 템플릿 HPA. kind에서 metrics-server + HPA `cpu:1%/70%` 판독 검증.
- 2026-08-25 — 멀티테넌트 보안 하드닝(hardening/) 추가: 신뢰 등급별 네임스페이스(baseline/restricted), ResourceQuota/LimitRange, NetworkPolicy(deny-by-default), gVisor RuntimeClass, Kaniko 빌드 Job 템플릿. 서비스 템플릿에 securityContext 강화. kind에서 restricted 루트 파드 거부 검증.
