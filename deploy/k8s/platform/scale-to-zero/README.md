# Scale-to-Zero (KEDA HTTP add-on)

수만~수십만 사용자 환경에서 바이브 코더가 올린 서비스 다수는 **거의 호출되지 않는다**.
유휴 서비스가 파드를 계속 점유하면 비용이 선형으로 증가한다. KEDA HTTP add-on 으로
**유휴 서비스를 0으로 축소**하고, 요청이 오면 인터셉터가 **0→1 콜드스타트**한다.

## 구성 요소
- **KEDA core**: `keda-operator`, `metrics-apiserver`, `admission-webhooks`.
- **HTTP add-on**: `interceptor`(요청을 보류하며 스케일 유발), `external-scaler`, `controller`.
- **HTTPScaledObject**: 서비스별 스케일 정책(min/max, scaledownPeriod, host).

## 설치 (helm)
```bash
helm repo add kedacore https://kedacore.github.io/charts && helm repo update kedacore
helm install keda kedacore/keda -n keda --create-namespace --wait
helm install http-add-on kedacore/keda-add-ons-http -n keda --wait
```

## 서비스 적용
1. 서비스별로 `deploy/k8s/scale-to-zero-template.yaml` 를 렌더링해 `HTTPScaledObject` 생성
   (`{{SLUG}}/{{NAMESPACE}}/{{PORT}}/{{HOST}}` 치환).
2. 해당 서비스 Ingress 백엔드를 KEDA 인터셉터
   (`keda-add-ons-http-interceptor-proxy.keda:8080`)로 지정하고 `Host: {{HOST}}` 전달.
   → 직접 서비스로 보내면 스케일 유발이 되지 않는다(인터셉터를 반드시 경유).

## 동작 검증 (kind, 2026-08-25)
- 샘플 `s2z-app`(nginx) + `HTTPScaledObject`(min:0, scaledownPeriod:30) 적용.
- **유휴 → `spec.replicas=0`** 도달 확인.
- 인터셉터 경유 요청(`Host: s2z.edu.internal`) → **HTTP 200, 2.85s(콜드스타트)**, 배포 **0→1**, ready=1.
- 결론: 유휴 0비용 + 요청 시 자동 기동 동작 확인.

## 주의
- 콜드스타트 지연(첫 요청 수 초)이 UX에 허용되는 서비스에 적용.
- 상시 저지연이 필요한 서비스는 `replicas.min: 1` 로 최소 1 유지.
- 인터셉터가 요청 경로에 들어오므로 인터셉터 자체는 HA(복제)로 운영.
