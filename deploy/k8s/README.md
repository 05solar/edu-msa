# K8s 매니페스트 · edu-msa

## 구성

```
deploy/k8s/
├── namespaces.yaml          # edu-platform, edu-services
├── platform/
│   ├── postgres.yaml        # DB (Secret/PVC/Deployment/Service)
│   ├── backend.yaml         # 플랫폼 API
│   ├── frontend.yaml        # 정적 프론트(nginx)
│   ├── ingress.yaml         # edu.internal → /api=backend, /=frontend
│   └── rbac.yaml            # edu-deployer SA + edu-services 배포 권한
└── service-template.yaml    # 서비스 1개당 렌더링되는 템플릿(백엔드가 채움)
```

## 적용 순서

```bash
kubectl apply -f namespaces.yaml
kubectl apply -f platform/rbac.yaml
kubectl apply -f platform/postgres.yaml
kubectl apply -f platform/backend.yaml
kubectl apply -f platform/frontend.yaml
kubectl apply -f platform/ingress.yaml
```

이미지는 `registry.edu.internal/edu-msa-backend`,
`registry.edu.internal/edu-msa-frontend`로 빌드·push 되어 있어야 한다.

## 바이브 코더 서비스 배포

플랫폼 백엔드의 배포 파이프라인이 각 서비스의 `service.yaml`을 읽어
`service-template.yaml`을 렌더링한 뒤 `edu-services` 네임스페이스에 적용한다.
- Deployment/Service/Ingress 이름 = `slug`
- 경로 = `https://edu.internal/svc/<slug>`
- readiness/liveness probe = `service.yaml`의 `health` 경로

렌더링/적용은 `EDU_DEPLOY_MODE=real`(kubectl 실행) 또는 `simulate`(매니페스트만 생성)로
동작한다. 자세한 계약은 [../../docs/MSA_SERVICE_SPEC.md](../../docs/MSA_SERVICE_SPEC.md).
