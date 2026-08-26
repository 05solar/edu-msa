# GPU · 테넌트 서비스가 GPU를 쓰게 하기

이 플랫폼의 **코어(frontend·backend·auth·PostgreSQL)는 GPU가 필요 없다. CPU만으로 동작**한다.
GPU는 **배포되는 테넌트 서비스**(예: OCR·데이터요약을 실제 ML 모델로 돌리는 도구)가 요청할 때만
의미가 있다.

## 1. 무엇이 갖춰져야 하나

| 요소 | 역할 |
|---|---|
| GPU 노드 | 물리 GPU + NVIDIA 드라이버 + container toolkit |
| **NVIDIA GPU Operator** | driver/toolkit/device-plugin/DCGM 을 클러스터에 자동 구성 |
| device-plugin | 노드의 GPU를 `nvidia.com/gpu` 확장 리소스로 광고 |

설치(한 번):
```bash
WITH_GPU=1 ./deploy/bootstrap.sh gpu
# 내부적으로: helm upgrade --install gpu-operator nvidia/gpu-operator -n gpu-operator --create-namespace
```
확인:
```bash
kubectl -n gpu-operator get pods
kubectl get nodes -o=jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.allocatable.nvidia\.com/gpu}{"\n"}{end}'
```

## 2. 테넌트가 GPU를 요청하는 법 (service.yaml)

서비스 레포의 `service.yaml` 에 `resources.gpu` 를 1 이상으로 지정하면, 배포 파이프라인이
매니페스트 `limits` 에 `nvidia.com/gpu` 를 자동으로 추가한다.

```yaml
name: 문서 OCR 추출기
slug: doc-ocr
category: civil
port: 8080
health: /healthz
resources:
  cpu: "1"
  memory: "2Gi"
  gpu: 1          # ← GPU 1장 요청 (0 또는 생략 시 GPU 미사용)
```

렌더링 결과(발췌):
```yaml
resources:
  requests: { cpu: "1", memory: "2Gi" }
  limits:   { cpu: "500m", memory: "512Mi", "nvidia.com/gpu": "1" }
```
> 검증 규칙: `resources.gpu` 는 0~8. 스케줄되려면 위 GPU Operator 가 설치돼 있어야 한다.
> (관련 코드: `backend/.../deploy/{SpecParser,ManifestRenderer,ServiceSpecValidator}.java`,
> 템플릿 `deploy-templates/service-template.yaml` 의 `{{GPU_LIMIT}}`)

## 3. GPU 노드에 taint 가 있으면

일부 운영 클러스터는 GPU 노드를 taint 해 일반 파드가 못 올라오게 막는다
(예: `nvidia.com/gpu=present:NoSchedule`). 이 경우 테넌트 파드에 toleration 이 필요하다.

- 개별 서비스에 즉시 적용:
  ```bash
  kubectl -n edu-services-public patch deploy <slug> \
    --patch-file deploy/k8s/platform/gpu/gpu-toleration-patch.yaml
  ```
- 모든 서비스에 항구 적용: `deploy/k8s/service-template.yaml` 과
  `backend/src/main/resources/deploy-templates/service-template.yaml` 의 `template.spec` 에
  `gpu-toleration-patch.yaml` 의 `tolerations` 블록을 추가.

## 4. 격리 주의

GPU 를 쓰는 **공개(비신뢰) 서비스**는 커널을 공유한다. gVisor 샌드박스는 GPU 패스스루와
호환이 제한적이므로, GPU 테넌트는 다음 중 하나를 권장한다.
- GPU 테넌트를 **내부(edu-services, baseline)** 로 한정하고 공개 tier에서는 GPU 비허용, 또는
- MIG(Multi-Instance GPU)로 물리 분할 + 전용 노드풀 격리.

`runtimeClassName: nvidia` 가 필요한 환경이면 `nvidia-runtimeclass.yaml` 을 적용하고 파드에 지정한다.
