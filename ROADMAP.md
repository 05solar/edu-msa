# ROADMAP.md · 대규모 전환 로드맵

현재 구조는 소규모엔 적합하나 대규모(수십만 사용자·수천 서비스)엔 아래 축이 비어 있다.
평가 근거는 [SECURITY.md](SECURITY.md)와 검증 기록(PROCESS.md) 참고. 각 항목은
**구현 → 로컬(kind) 테스트/검증 → 문서화** 순으로 순차 진행한다.

## P0 (대규모 필수 · 없으면 못 버팀)

- [x] **P0-1 오토스케일** — HPA + PodDisruptionBudget (완료·검증)
  - 구현: `deploy/k8s/platform/autoscale.yaml`(backend/frontend HPA+PDB), 서비스 템플릿에 HPA 추가(동적 서비스 자동확장).
  - 검증(kind): metrics-server 설치, workdays HPA가 `cpu: 1%/70%` 메트릭 정상 판독, PDB 활성. (Cluster Autoscaler는 실서버 노드풀 필요)
- [x] **P0-2 배포 오케스트레이션 분리** — DB 작업 큐 + 워커 (완료·검증)
  - 구현: `DeployJob` 엔티티/큐, `DeployJobService`(claimNext = `FOR UPDATE SKIP LOCKED` 행잠금),
    `DeployWorker`(@Scheduled 폴링, 재시도), 승인/등록은 큐에 적재(비동기 202). @EnableScheduling.
  - 검증: 적재→워커 처리→`done`(deploymentId 부여·컨테이너 기동), 실패 소스는 attempts 0→1→2 후 `failed`.
- [x] **P0-3 데이터베이스 HA** — CloudNativePG 3-인스턴스 클러스터 (완료·검증)
  - 구현: `deploy/k8s/platform/postgres-ha.yaml`(Cluster instances 3, rw/ro 서비스), backend.yaml을 `edu-db-rw` + `edu-db-app` 시크릿으로 전환.
  - 검증(kind): 3/3 healthy(primary+replica 2), **primary 파드 삭제 → 복제본 자동 승격(edu-db-1→edu-db-2) → 3/3 재수렴**.

## P1 (프로덕션 안전)

- [ ] **P1-1 안전 빌드** — docker.sock 제거 → Kaniko + 레지스트리, 신뢰도별 네임스페이스 자동배치
- [x] **P1-2 NetworkPolicy 강제** — Calico 도입 (완료·검증)
  - 구현: kind를 `disableDefaultCNI`로 재생성 + Calico v3.28 설치, hardening NetworkPolicy 적용.
  - 검증: 공개 tier 외부 egress **차단**(DNS만 허용), 내부 tier 인터넷 **허용**, 정책 없는 ns는 개방 → 정책이 실제 강제됨. PodSecurity baseline/restricted 확인.
- [ ] **P1-3 가용성** — PDB(플랫폼), 멀티AZ, 롤링/카나리 전략

## P2 (운영 성숙도)

- [ ] **P2-1 유휴 비용** — scale-to-zero(Knative/KEDA)
- [ ] **P2-2 관측성** — Prometheus/Grafana/Loki/Tempo, 감사로그, 이미지 스캔
- [ ] **P2-3 엣지** — TLS·rate-limit·WAF·CDN

## 진행 이력
- 2026-08-25 — 로드맵 작성. P0-1(오토스케일) 착수.
- 2026-08-25 — P0-1 완료·검증(HPA `cpu:1%/70%` 판독, PDB).
- 2026-08-25 — P0-2 완료·검증(작업 큐+워커, done/재시도→failed 확인).
- 2026-08-25 — P0-3 완료·검증(CloudNativePG 3-인스턴스, primary 삭제→자동 승격 확인). **P0 전부 완료.**
- 2026-08-25 — P1-2 완료·검증(Calico 재생성 + NetworkPolicy 강제: 공개 tier egress 차단/내부 tier 허용 대조). 다음: P1-1 안전빌드·네임스페이스 자동배치.
