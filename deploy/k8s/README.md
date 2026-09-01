# K8s 매니페스트 · edu-msa

## 구성

```
deploy/k8s/
├── namespaces.yaml          # edu-platform, edu-services
├── platform/
│   ├── postgres.yaml        # 플랫폼 DB (Secret/PVC/Deployment/Service)
│   ├── backend.yaml         # 플랫폼 API
│   ├── frontend.yaml        # 정적 프론트(nginx)
│   ├── ingress.yaml         # edu.internal → /api/auth=auth-service, /api=backend, /=frontend
│   └── rbac.yaml            # edu-deployer SA + edu-services 배포 권한
├── auth/
│   ├── auth-db.yaml         # 인증 DB (Secret/PVC/Deployment/Service)
│   └── auth-service.yaml    # 인증 API (Secret/Deployment/Service)
└── service-template.yaml    # 서비스 1개당 렌더링되는 템플릿(백엔드가 채움)
```

## 적용 순서

```bash
kubectl apply -f namespaces.yaml
kubectl apply -f platform/rbac.yaml
kubectl apply -f platform/postgres.yaml
kubectl apply -f auth/auth-db.yaml
kubectl apply -f auth/auth-service.yaml     # backend 보다 먼저 — edu-auth-jwt Secret 을 만든다
kubectl apply -f platform/backend.yaml
kubectl apply -f platform/frontend.yaml
kubectl apply -f platform/ingress.yaml
```

이미지는 `registry.edu.internal/edu-msa-backend`,
`registry.edu.internal/edu-msa-auth-service`,
`registry.edu.internal/edu-msa-frontend`로 빌드·push 되어 있어야 한다.

## 시크릿

매니페스트에 들어 있는 Secret 값은 형태를 보여주는 자리표시자다. 실제 환경에서는 apply 전에
새로 생성한다.

```bash
kubectl -n edu-platform create secret generic edu-auth-jwt \
  --from-literal=EDU_JWT_SECRET="$(openssl rand -base64 48)" \
  --from-literal=EDU_SEED_PASSWORD="$(openssl rand -base64 12)"

kubectl -n edu-platform create secret generic edu-auth-db \
  --from-literal=POSTGRES_DB=eduauth \
  --from-literal=POSTGRES_USER=eduauth \
  --from-literal=POSTGRES_PASSWORD="$(openssl rand -base64 24)"
```

`EDU_JWT_SECRET` 은 auth-service(발급)와 backend(검증)가 **같은 값**을 참조해야 한다.
두 Deployment 모두 `edu-auth-jwt` Secret 을 바라보므로 값은 한 곳에서만 관리된다.

## 바이브 코더 서비스 배포

플랫폼 백엔드의 배포 파이프라인이 각 서비스의 `service.yaml`을 읽어
`service-template.yaml`을 렌더링한 뒤 `edu-services` 네임스페이스에 적용한다.
- Deployment/Service/Ingress 이름 = `slug`
- 경로 = `https://edu.internal/svc/<slug>`
- readiness/liveness probe = `service.yaml`의 `health` 경로

렌더링/적용은 `EDU_DEPLOY_MODE=real`(kubectl 실행) 또는 `simulate`(매니페스트만 생성)로
동작한다. 자세한 계약은 [../../docs/architecture/MSA_SERVICE_SPEC.md](../../docs/architecture/MSA_SERVICE_SPEC.md).
