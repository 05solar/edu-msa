# ROADMAP.md · 대규모 전환 로드맵

현재 구조는 소규모엔 적합하나 대규모(수십만 사용자·수천 서비스)엔 아래 축이 비어 있다.
평가 근거는 [SECURITY.md](SECURITY.md)와 검증 기록(PROCESS.md) 참고. 각 항목은
**구현 → 로컬(kind) 테스트/검증 → 문서화** 순으로 순차 진행한다.

## P0 (대규모 필수 · 없으면 못 버팀)

- [x] **P0-1 오토스케일** — HPA + PodDisruptionBudget (완료·검증)
  - 구현: `deploy/k8s/platform/autoscale.yaml`(backend/frontend HPA+PDB), 서비스 템플릿에 HPA 추가(동적 서비스 자동확장).
  - 검증(kind): metrics-server 설치, workdays HPA가 `cpu: 1%/70%` 메트릭 정상 판독, PDB 활성. (Cluster Autoscaler는 실서버 노드풀 필요)
- [ ] **P0-2 배포 오케스트레이션 분리** — 인메모리 스레드풀 → **DB 기반 작업 큐 + 워커**
  - 멱등·재시도·다중 replica 안전(행 잠금/리스). 검증: 큐 적재→워커 처리→상태 전이.
- [ ] **P0-3 데이터베이스 HA** — 단일 Deployment → StatefulSet/오퍼레이터(CloudNativePG) 또는 관리형
  - 검증: primary/replica 구성, 장애 시 승격.

## P1 (프로덕션 안전)

- [ ] **P1-1 안전 빌드** — docker.sock 제거 → Kaniko + 레지스트리, 신뢰도별 네임스페이스 자동배치
- [ ] **P1-2 NetworkPolicy 강제** — Calico/Cilium 도입(현재 kindnet 미강제)
- [ ] **P1-3 가용성** — PDB(플랫폼), 멀티AZ, 롤링/카나리 전략

## P2 (운영 성숙도)

- [ ] **P2-1 유휴 비용** — scale-to-zero(Knative/KEDA)
- [ ] **P2-2 관측성** — Prometheus/Grafana/Loki/Tempo, 감사로그, 이미지 스캔
- [ ] **P2-3 엣지** — TLS·rate-limit·WAF·CDN

## 진행 이력
- 2026-08-25 — 로드맵 작성. P0-1(오토스케일) 착수.
- 2026-08-25 — P0-1 완료·검증(HPA `cpu:1%/70%` 판독, PDB). 다음: P0-2 배포 오케스트레이션 큐+워커.
