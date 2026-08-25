# 로그 수집 (Loki + Promtail)

Promtail(노드 DaemonSet)이 모든 파드 로그를 수집해 Loki로 전송하고,
Grafana(관측성 스택)에서 LogQL로 조회한다. 메트릭(P2-2)과 함께 운영 가시성을 완성한다.

## 설치 (helm)
```bash
helm repo add grafana https://grafana.github.io/helm-charts && helm repo update grafana
helm install loki grafana/loki-stack -n logging --create-namespace -f loki-values.yaml
```

## Grafana 연동
```bash
kubectl apply -f loki-grafana-datasource.yaml
```
kube-prometheus-stack Grafana 사이드카가 `grafana_datasource=1` ConfigMap을 자동 로드해
Loki 데이터소스(`http://loki.logging:3100`)를 추가한다. Grafana > Explore에서 LogQL 조회.

## 조회 예 (LogQL)
```
{namespace="edu-services-public"}                 # 공개(미신뢰) 서비스 로그
{namespace="edu-platform", pod=~"backend.*"} |= "ERROR"
{namespace="default"} |= "특정-토큰"
```

## 동작 검증 (kind, 2026-08-25)
- loki-0 / loki-promtail Running.
- 테스트 파드가 고유 토큰 5줄 로그 출력 → 약 25초 후 Loki 조회
  `{namespace="default"} |= "EDU-LOKI-VERIFY-8842"` → **status=success, 5줄 매칭**.
- 스트림 라벨(namespace/pod/container/job) 정상 추출 확인.
- Grafana 사이드카가 Loki 데이터소스 자동 로드 확인(provisioning 파일 기록 로그).
- 참고: 검증은 emptyDir(persistence off)로 수행 — PVC 경로 자체는 미검증(로그 파이프라인 결과는 동일).

## 남은 항목(프로덕션)
- **차트 마이그레이션**: `grafana/loki-stack`은 deprecated → 유지보수되는 `grafana/loki`(분산 모드) + 오브젝트 스토리지로 이전.
- 오브젝트 스토리지 백엔드(S3/GCS) + 보존기간/압축, 멀티테넌시(orgID).
- 트레이스(Tempo) + 로그-트레이스 상관(trace_id), 감사로그 별도 파이프라인/보존.
- PII 마스킹, 로그 접근 통제(미신뢰 서비스 로그 격리).
