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
- 2026-08-25 — P0-3 DB HA: CloudNativePG 오퍼레이터 + postgres-ha.yaml(Cluster instances 3), backend.yaml을 edu-db-rw + edu-db-app 시크릿으로 전환. kind 검증: 3/3 healthy, primary 삭제→복제본 자동 승격(edu-db-1→2)→재수렴. P0 전부 완료.
- 2026-08-25 — 대규모 로드맵(ROADMAP.md) 착수. P0-1 오토스케일: platform/autoscale.yaml(HPA+PDB), 서비스 템플릿 HPA. kind에서 metrics-server + HPA `cpu:1%/70%` 판독 검증.
- 2026-08-25 — 멀티테넌트 보안 하드닝(hardening/) 추가: 신뢰 등급별 네임스페이스(baseline/restricted), ResourceQuota/LimitRange, NetworkPolicy(deny-by-default), gVisor RuntimeClass, Kaniko 빌드 Job 템플릿. 서비스 템플릿에 securityContext 강화. kind에서 restricted 루트 파드 거부 검증.
