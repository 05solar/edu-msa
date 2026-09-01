# VERSIONS.md · 버전 관리 & 고도화 이력

플랫폼이 **어떤 순서로 고도화되어 왔고**, 버전을 **어떻게 관리하며**, **앞으로 무엇을 어떤
프로세스로 진행할지**를 한곳에 정리한다. 세부 검증 기록은 [ROADMAP.md](ROADMAP.md)·
[PROCESS.md](PROCESS.md), 배포 방법은 [DEPLOY.md](../operations/DEPLOY.md) 참고.

---

## 1. 버전 관리 정책

### 현재 상태

- **브랜치**: `main` 단일 트렁크. 큰 기능은 `feature/*` 브랜치에서 작업 후 merge
  (예: `feature/auth-service` → `1ddc05f` 병합).
- **커밋 컨벤션**: `feat / fix / docs / deploy / refine + 한글 요약`. 커밋 하나가
  "구현 + 검증 + 문서 갱신"을 담는 단위다.
- **태그**: 아직 없음 (아래 도입 예정 규칙 참고).
- **테넌트 서비스 버전**: 각 서비스 레포의 `service.yaml` `version` 필드(SemVer)가
  기준이며, 플랫폼 카탈로그·배포 이미지 태그에 그대로 쓰인다.

### 도입할 규칙 (플랫폼 버전)

- **SemVer** `MAJOR.MINOR.PATCH` 를 git 태그로 관리한다.
  - `MINOR` — 기능 단계 완료(아래 §2의 한 단계에 해당)마다 올린다.
  - `PATCH` — 버그 수정·문서 보완만 있을 때.
  - `MAJOR` — 규격(service.yaml·API) 하위호환이 깨질 때만.
- 소급 태깅 기준선: 지금까지의 이력을 아래처럼 대응시킨다.

| 태그 | 시점 | 내용 (§2 단계) |
|---|---|---|
| `v0.1.0` | 2026-08-24 | 플랫폼 기반 골격 (0단계) |
| `v0.2.0` | 2026-08-25 | 인프라 고도화 P0~P3 (1단계) |
| `v0.3.0` | 2026-08-25 | 기본 서비스 7종 1차 (2단계) |
| `v0.4.0` | 2026-08-26 | 인증 마이크로서비스 (3단계) |
| `v0.5.0` | 2026-08-26 | 개인용 단발 도구 7종 재편 + UI 고도화 (4단계) |
| `v0.6.0` | 2026-08-31 | 원커맨드 배포·GPU·운영 문서 (5단계) |
| `v0.7.0` | (예정) | Gitea 내부 저장소 (§3) |
| `v1.0.0` | (예정) | 실서버 정식 개통 (§4 백로그 P급 소진 시) |

```bash
# 릴리스 시
git tag -a v0.6.0 -m "원커맨드 배포·GPU·운영 문서"
git push origin v0.6.0
```

---

## 2. 지금까지의 고도화 이력 (순차)

총 73커밋(2026-08-24 ~ 08-31). 시간 순서대로 6단계로 진행했다.

### 0단계 — 플랫폼 기반 골격 (08-24, `9cb3dc8`~`b14919a`)

- React 프론트 + Spring Boot 백엔드의 MSA 플랫폼 뼈대, GitHub Actions CI.
- 업무 분야별 실동작 예제, **등록 승인 시 자동 배포** 파이프라인의 첫 형태.
- AI 빌드 지시서(AI_BUILD_SPEC) — "레포 규격에 맞춰 AI로 프로그램을 만드는" 흐름 정립.

### 1단계 — 인프라 고도화 P0~P3 (08-25, `524b3c6`~`6548758`)

대규모(수십만 사용자·수천 서비스) 대비 축을 하루에 P0→P3 순서로 구현·검증. 각 항목은
kind 로컬 검증 + 서브에이전트 리뷰 PASS 후 완료 처리([ROADMAP.md](ROADMAP.md) 검증 기록).

| 항목 | 내용 |
|---|---|
| P0-1 오토스케일 | HPA + PodDisruptionBudget |
| P0-2 배포 오케스트레이션 | DB 작업 큐 + 워커(FOR UPDATE SKIP LOCKED, 재시도) |
| P0-3 DB HA | CloudNativePG 3-인스턴스 (primary 삭제→자동 승격 검증) |
| P1-1 안전 빌드 | 신뢰도별 네임스페이스 자동배치 + **Kaniko 인클러스터 빌드**(docker.sock 제거) |
| P1-2 네트워크 격리 | Calico NetworkPolicy 강제 (공개 tier 외부 egress 차단) |
| P1-3 가용성 | 무중단 롤링 + PDB + AZ 분산/안티어피니티 |
| P2-1 유휴 비용 | KEDA HTTP add-on scale-to-zero |
| P2-2 관측성 | kube-prometheus-stack + 백엔드 메트릭 |
| P2-3 엣지 보안 | TLS + rate-limit + WAF(ModSecurity/OWASP CRS) |
| P3-1 자동 TLS | cert-manager 발급자 체인(edu-ca) |
| P3-2 로그·트레이스 | Loki + Promtail, Tempo + 트레이스↔로그 상관 |
| P3-3 알림 | Alertmanager + PrometheusRule |
| P3-4 레지스트리 | kind-local-registry — 빌드→push→노드 pull 전 구간 완결 |

### 2단계 — 기본 서비스 1차 구축 (08-25, `2544885`~`f384923`)

- 부서 업무 시스템형 서비스 7종(Go·Python·Java·TS·C#·Rust·Kotlin — 언어별 규격 검증 겸함)
  + Seed 등록 + docker 배포 검증.

### 3단계 — 인증 마이크로서비스 (08-25~26, `a45c635`~`e2705f8`, `1ddc05f` 병합)

- `auth-service` 신설: 계정 단일 소스, BCrypt + HS256 JWT, HttpOnly Refresh 쿠키.
- 각 자원 서버가 동일 시크릿으로 **JWT 자체 검증** (동기 의존 없음).
- 최소 권한 원칙: 가입은 항상 USER, 상향(CODER/ADMIN)은 신청 → 운영 관리자 승인.
- 포털 인증 화면 4종(로그인·회원가입·아이디/비밀번호 찾기).

### 4단계 — 개인용 단발 도구 재편 + UI 고도화 (08-26, `20e350b`~`4e7f002`)

- 기본 서비스를 "부서 시스템"에서 **개인용 단발 도구 7종**으로 전환
  (맞춤법 검사기·자리배치·시간표·여비 계산·QR 라벨·통계 차트·OCR).
- **서브도메인 라우팅** 도입: `<slug>.localhost` (Traefik/Ingress Host 라우팅).
- 7종 웹 UI 통일 디자인 시스템, OG 미리보기·파비콘.

### 5단계 — 안정화·운영 편의 (08-26~08-31, `1fe535e`~`c68fdb4`)

- 로그인·카탈로그·권한 전환 관련 버그 수정, 배포 진행 상태 실시간 표시.
- 전체 문서 리뉴얼 + 시스템 개요 시각 문서(`system-overview.html`).
- **원커맨드 배포기**(`deploy/bootstrap.sh` + Makefile): 클러스터 준비→이미지 빌드→코어→
  운영스택→GPU까지 한 번에. 테넌트 GPU 지원(`resources.gpu`).
- 예제 7종 K8s 배포 서브커맨드(`bootstrap.sh examples`) — local:// 시드도 실서버 배포 가능.

---

## 3. 예정 — Gitea 내부 저장소 도입

> 단계별 상세 작업·기간·검증 시나리오는 **[docs/GITEA_PLAN.md](GITEA_PLAN.md)** 참고.

지금은 문서·UI가 "GitHub 레포 등록"을 안내하지만, 운영 목표 환경(내부망)에서는 코드가
외부로 나가면 안 된다. 이미 카탈로그의 제공 방식에 **"Gitea 저장소(내부망 전용)"** 가
정의되어 있고 시드 데이터도 `gitea.edu.internal` 주소를 쓰므로, 실제 Gitea 서버를
플랫폼 스택에 추가한다.

### 계획

1. **설치** — `deploy/k8s/platform/gitea/`에 Helm 기반 매니페스트 추가,
   `bootstrap.sh stack` 단계에 편입. 접속은 `gitea.<DOMAIN>` (cert-manager TLS).
2. **파이프라인 연결** — 등록 화면·검증기(`repoUrl` 정규식)가 내부 Gitea 주소를
   1급으로 허용하고, Kaniko 빌드가 Gitea에서 clone 하도록 자격 증명(시크릿) 배선.
3. **계정 연동** — auth-service 계정과 연결(1차: 수동 발급, 2차: OAuth2/SSO 위임).
4. **자동 재배포** — Gitea webhook → `POST /api/programs/{id}/deploy` 로 push 시
   자동 재배포(브랜치=main 한정, 서명 검증).
5. **이관** — 기존 GitHub 안내 문서(VIBE_CODING_GUIDE 등)를 "내부 Gitea 기준 + 외부
   GitHub 병용"으로 갱신, local:// 예제는 Gitea 레포로 미러링.

완료 기준: 내부 Gitea 레포 주소만으로 **등록→검증→빌드→배포→공개** 전 구간이 동작하고,
push 시 자동 재배포까지 kind에서 검증되면 `v0.7.0` 태깅.

---

## 4. 추후 진행 백로그

ROADMAP 실구현 항목(P0~P3)은 완료. 잔여·신규 항목을 우선순위로 관리한다.

| 우선순위 | 항목 | 내용 | 비고 |
|---|---|---|---|
| A | **Gitea 도입** | §3 전체 | `v0.7.0` |
| A | 시크릿 관리 | `EDU_JWT_SECRET` 등 Vault/Sealed Secrets + 주기 회전 | P1-4 잔여 |
| A | 감사로그 | 관리자 처리·배포·권한 변경의 감사 파이프라인(Loki 라벨 표준화) | P3-2 잔여 |
| B | 교육청 SSO | auth-service 를 SSO(OIDC) 위임으로 확장 — 토큰 형태 동일이라 자원 서버 무변경 | P1-4 잔여 |
| B | 앱 OTel 계측 | backend·auth-service 실계측 + trace_id 로그 표준화 | P3-2 잔여 |
| B | 알림 수신처 | Alertmanager Slack/Email 라우팅·억제·SLO 규칙 | P3-3 잔여 |
| C | WAF CRS 튜닝 | 오탐 튜닝 + CDN/DDoS 앞단 | P2-3 잔여 |
| C | 대시보드 코드화 | Grafana 대시보드 as-code | P2-2 잔여 |
| C | 인증서 최적화 | 실서버 ACME 전환, 와일드카드 단일 인증서 | P3-1 잔여 |

`v1.0.0`(실서버 정식 개통)은 A급 소진 + 실서버(k3s/Calico) 리허설 통과를 조건으로 한다.

---

## 5. 진행 프로세스 (모든 항목 공통)

지금까지의 방식을 그대로 규칙화한다. 항목 하나는 아래 순서로만 완료 처리한다.

```
① 계획     백로그(§4)에서 선정, 완료 기준(검증 시나리오) 먼저 정의
② 구현     feature/* 브랜치 (규모가 작으면 main 직행 허용)
③ 검증     kind 로컬에서 실동작 검증 — "적용됨"이 아니라 "동작함"을 확인
            (예: primary 삭제→승격, WAF 403, 0→1 콜드스타트)
④ 리뷰     서브에이전트(또는 동료) 리뷰 PASS — 보안 지적은 반영 후 재검
⑤ 문서화   ROADMAP.md 체크·검증 기록, PROCESS.md 이력, 관련 문서 갱신
⑥ 병합     main merge + 커밋 컨벤션 준수
⑦ 릴리스   단계 완료 시 SemVer 태그 + 본 문서 §2 이력 추가
```

- **문서 갱신은 작업의 일부다** — 메타 문서(README/PROCESS/AGENT/DESIGN/TEST)를
  작업 시마다 갱신한다([AGENT.md](../../AGENT.md)). 본 문서는 ⑦ 릴리스 시점에 갱신한다.
- 실서버 반영은 kind 검증 통과본만. 실서버 차이점 체크리스트는
  [DEPLOY.md](../operations/DEPLOY.md) §실서버 체크리스트를 따른다.

## 갱신 이력

- 2026-09-01 — 문서 작성. 0~5단계 이력 정리, 태깅 기준선·Gitea 계획·백로그·프로세스 수립.
