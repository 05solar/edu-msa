# 관측성 (Prometheus + Grafana)

kube-prometheus-stack(Prometheus Operator + Prometheus + Grafana + kube-state-metrics +
node-exporter)으로 클러스터/플랫폼/서비스 메트릭을 수집·시각화한다.

## 설치 (helm)
```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update prometheus-community
helm install kps prometheus-community/kube-prometheus-stack -n monitoring --create-namespace \
  --set alertmanager.enabled=false \
  --set prometheus.prometheusSpec.retention=2h \
  --set grafana.adminPassword=<암호> --wait
```

## 백엔드 메트릭 노출
- 의존성: `io.micrometer:micrometer-registry-prometheus` (build.gradle.kts).
- 설정: `management.endpoints.web.exposure.include: health,info,prometheus`,
  공통 태그 `application=edu-msa-backend` (application.yml).
- 엔드포인트: `GET /actuator/prometheus` (JVM/HTTP/Hikari 등).
- 스크레이프: `backend-servicemonitor.yaml` (ServiceMonitor). Service 포트명 `http`,
  path `/actuator/prometheus`. **라벨 `release: kps`** 가 있어야 스택 Prometheus 가 선택.

## 서비스(테넌트) 메트릭
- 업로더 서비스가 Prometheus 형식 `/metrics`(또는 `/actuator/prometheus`)를 노출하면,
  동일 형태의 ServiceMonitor(라벨 `release: kps`, 대상 Service 라벨 selector, 포트명, path)로 수집.

## Grafana 접속
```bash
kubectl -n monitoring port-forward svc/kps-grafana 3000:80
# http://localhost:3000  (admin / 설치 시 지정 암호)
```
kube-state-metrics/node-exporter 대시보드가 기본 제공된다.

## 동작 검증 (kind, 2026-08-25)
- 스택 기동: Prometheus/Grafana/kube-state-metrics/node-exporter 모두 Running.
- **스크레이프 데이터 존재**: `up` 시리즈 11개(apiserver·kubelet·coredns·node-exporter·operator 등), 정상 타깃 up=1.
- **백엔드 메트릭**: `/actuator/prometheus` HTTP 200, `application="edu-msa-backend"` 라벨, JVM/Hikari 메트릭 확인.
- **ServiceMonitor 디스커버리**: 예제 앱 + `release: kps` ServiceMonitor 적용 → 약 30초 내
  `up{job="promex"}=1` 로 Prometheus 가 자동 발견·스크레이프 확인.

## 알림 (Alertmanager + PrometheusRule)
- Alertmanager 활성화: `helm upgrade kps ... --reuse-values --set alertmanager.enabled=true`.
- 규칙: `prometheus-rules.yaml`(라벨 `release: kps`) — EduBackendDown/EduPodCrashLooping/EduHighMemory.
  적용: `kubectl apply -f prometheus-rules.yaml`.
- 검증(kind, 2026-08-25): 규칙 4종 로드, 테스트 알림 `EduAlwaysFiring`이 Prometheus에서 **firing** →
  Alertmanager에 **active** 수신 확인(파이프라인 동작). 검증 후 테스트 알림은 비활성(주석) 처리.
- 남음: Alertmanager 수신처(Slack/Email/PagerDuty) 라우팅, 심각도별 억제/그룹핑, SLO 기반 규칙 확장.

## 남은 항목(프로덕션)
- 트레이스(Tempo) 파이프라인, 감사로그, 이미지 스캔 결과 연동.
- Grafana 대시보드 코드화(ConfigMap sidecar) 및 인증 연동.
