# 분산 트레이싱 (Tempo)

Grafana Tempo로 분산 트레이스를 저장·조회한다. 메트릭(P2-2)·로그(P3-2)와 함께
관측성 3축(metrics/logs/traces)을 완성하고, 트레이스↔로그 상관으로 원인 분석을 가속한다.

## 설치 (helm)
```bash
helm repo add grafana https://grafana.github.io/helm-charts && helm repo update grafana
helm install tempo grafana/tempo -n tracing --create-namespace -f tempo-values.yaml
```
`tempo-values.yaml`가 OTLP 수신(gRPC 4317 / HTTP 4318)을 활성화한다.

## 앱 계측(트레이스 전송)
- 서비스는 OpenTelemetry SDK로 스팬을 생성해 **OTLP**로 전송:
  엔드포인트 `tempo.tracing.svc.cluster.local:4317`(gRPC) 또는 `:4318`(HTTP).
- 로그에 `trace_id=<...>`를 남기면 로그↔트레이스 상관이 동작한다(아래 Grafana 연동).

## Grafana 연동 + 상관
```bash
kubectl apply -f tempo-grafana-datasource.yaml         # Tempo 데이터소스(uid: tempo)
kubectl apply -f ../logging/loki-grafana-datasource.yaml  # Loki(uid: loki) + derivedFields
```
- **트레이스→로그**: Tempo 데이터소스 `tracesToLogsV2.datasourceUid: loki` — 스팬에서 관련 로그로 이동.
- **로그→트레이스**: Loki `derivedFields`가 로그의 `trace_id=`를 인식해 Tempo 트레이스로 링크.
- Grafana > Explore에서 Tempo 선택 후 TraceQL/서비스로 조회.

## 동작 검증 (kind, 2026-08-25)
- tempo-0 Running, 서비스가 OTLP 4317/4318 + 쿼리 3200 노출.
- `telemetrygen`으로 `service=edu-trace-test` 트레이스 20개 전송 →
  Tempo 검색 API `/api/search?tags=service.name=edu-trace-test` → **traceID 20건 조회**,
  `rootServiceName=edu-trace-test` 확인.
- Grafana 사이드카가 Tempo 데이터소스 provisioning 로드 확인.

## 남은 항목(프로덕션)
- 오브젝트 스토리지 백엔드(S3/GCS), 보존기간, 샘플링(꼬리 기반) 정책.
- 백엔드/서비스 실제 OTel 계측(자동/수동), 서비스 그래프(metrics-generator).
- 로그에 trace_id 주입 표준화(공통 로깅 포맷)로 상관 완성.
