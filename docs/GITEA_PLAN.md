# GITEA_PLAN.md · 내부 Gitea 구축 계획

내부망 git 저장소(Gitea)를 플랫폼 스택에 추가하기 위한 **상세 실행 계획**이다.
배경·우선순위는 [VERSIONS.md](../VERSIONS.md) §3, 완료 시 `v0.7.0` 태깅.

> **왜 Gitea인가** — 운영 목표 환경(교육청 내부망)에서는 소스코드가 외부(GitHub)로
> 나갈 수 없다. 카탈로그의 제공 방식에 이미 "Gitea 저장소(내부망 전용)"가 정의되어
> 있고 시드 데이터도 `gitea.edu.internal` 주소를 쓰므로, 실제 서버만 세우면 된다.
> Gitea는 오픈소스·경량(0.5~1 core / 512Mi~1Gi)이며 GitHub과 사용법이 거의 같다.

---

## 전체 일정 요약

| 단계 | 내용 | 예상 기간 | 산출물 |
|---|---|---|---|
| 1 | Gitea 설치 (Helm + PVC + DB) | 0.5일 | `deploy/k8s/platform/gitea/` |
| 2 | 도메인·TLS·Ingress 배선 | 0.5일 | `gitea.<DOMAIN>` HTTPS 접속 |
| 3 | 배포 파이프라인 연동 (clone 자격 증명) | 1일 | Gitea 레포로 등록→배포 동작 |
| 4 | webhook 자동 재배포 | 0.5일 | push → 자동 재배포 |
| 5 | 예제 미러링 + 문서 이관 | 0.5일 | local:// → Gitea 레포 |
| 6 | 통합 검증(kind) + 릴리스 | 0.5~1일 | `v0.7.0` 태그 |
| — | **1차 범위 합계** | **3.5~4일** | |
| 7 | (2차) SSO 계정 연동 | 별도 3~5일 | OIDC 위임 |

- 기간은 1인 기준, 각 단계는 "구현 → kind 검증 → 문서화" 포함.
- 1~2단계만으로도 "내부 Gitea에서 코드 열람/clone"은 즉시 가능(수동 계정).
- 7단계(SSO)는 auth-service 확장이 필요한 별개 프로젝트라 1차 범위에서 제외한다.

---

## 1단계 — Gitea 설치 (0.5일)

**작업**

1. Helm 리포 추가 및 설치 블록을 `deploy/bootstrap.sh`의 운영스택(`stack`) 절차에
   편입한다. 기존 `_try "cert-manager" helm upgrade --install …` 패턴(각 단계
   best-effort)을 그대로 따른다.
   ```bash
   helm repo add gitea-charts https://dl.gitea.com/charts/
   _try "Gitea (내부 저장소)" helm upgrade --install gitea gitea-charts/gitea \
     -n gitea --create-namespace -f deploy/k8s/platform/gitea/values.yaml
   ```
2. `deploy/k8s/platform/gitea/values.yaml` 작성 — 핵심 값:
   - `persistence.size`: 10Gi 시작(레포 데이터). StorageClass는 kind 기본/실서버 `local-path`.
   - DB: 1차는 차트 내장 옵션으로 시작해도 되나, **플랫폼 PostgreSQL(CNPG)에
     `gitea` DB를 추가**해 외부 DB로 붙이는 것을 권장(백업 일원화).
   - `gitea.admin.*`: 초기 관리자 계정 — 값 하드코딩 금지, Secret 주입.
   - 리소스 요청: cpu 250m / mem 512Mi, limit cpu 1 / mem 1Gi.
   - SSH: 내부망 HTTP(S) clone만 쓸 것이므로 **ssh 서비스 비활성**(포트 22 배선 생략).
3. README(`deploy/k8s/platform/gitea/README.md`)에 설치·초기 설정·백업 명령 기록.

**완료 기준** — `kubectl -n gitea get pods` 1/1 Running, 포트포워딩으로 웹 UI 접속·
관리자 로그인·테스트 레포 생성/clone 성공.

---

## 2단계 — 도메인·TLS·Ingress (0.5일)

**작업**

1. Ingress 추가: host `gitea.<DOMAIN>` (kind: `gitea.localhost` / 실서버:
   `gitea.edu.internal` 등) → gitea-http 서비스.
2. TLS: 기존 패턴 그대로 `cert-manager.io/cluster-issuer: edu-ca` 주석 + `spec.tls`
   — 인증서 자동 발급.
3. 대용량 push 대비 ingress-nginx 주석: `proxy-body-size: 512m`.
4. Gitea `ROOT_URL`을 `https://gitea.<DOMAIN>/`으로 설정(웹 링크·clone URL 일치).

**완료 기준** — 브라우저에서 `https://gitea.<DOMAIN>` 접속(우리 CA 인증서),
`git clone https://gitea.<DOMAIN>/<org>/<repo>.git` 성공.

---

## 3단계 — 배포 파이프라인 연동 (1일)

현재 파이프라인은 `SourceResolver.fromGit()`이 `git clone`으로 아무 https 레포나
받을 수 있으므로 **공개(public) Gitea 레포는 지금도 동작한다.** 이 단계의 실제 작업은
비공개 레포 자격 증명과 주소 검증·안내 정비다.

**작업**

1. **읽기 전용 봇 계정** — Gitea에 `edu-deploy-bot` 계정 + 조직 read 권한 +
   액세스 토큰 발급. 토큰은 K8s Secret(`edu-gitea-token`)으로 관리.
2. **clone 자격 증명 주입** — `SourceResolver.fromGit()`이 내부 Gitea 호스트일 때
   토큰을 사용하도록 수정(환경변수 `EDU_GITEA_HOST` / `EDU_GITEA_TOKEN`).
   Kaniko 경로(`ManifestRenderer.renderKanikoJob`)의 git 컨텍스트에도 동일 적용.
   - 주의: `ManifestRenderer`의 `REPO_RE`(repoUrl/branch 인자 주입 방지 정규식)가
     내부 도메인 형식을 허용하는지 확인·보강. 토큰이 로그·매니페스트에 평문
     노출되지 않도록 Secret 참조로 주입한다.
3. **등록 화면 안내 갱신** — placeholder·검증 문구를
   "내부 Gitea 주소(권장) 또는 GitHub 주소"로 변경.
4. **시드 주소 정합** — 프론트 목업(`programs.ts`)의 `gitea.edu.internal` 주소를
   실제 도메인 규칙과 일치시킨다.

**완료 기준(kind)** — 비공개 Gitea 레포 주소로 등록 → 규격 검증 통과 → (real 모드)
Kaniko 빌드 → 배포 → 서비스 200. 토큰 없는 외부 요청으로는 clone 불가 확인.

---

## 4단계 — webhook 자동 재배포 (0.5일)

**작업**

1. backend에 `POST /api/webhooks/gitea` 엔드포인트 추가:
   - Gitea webhook 시크릿(HMAC-SHA256) 서명 검증 — 불일치 시 401.
   - push 이벤트의 `repository.clone_url` + `ref`(main만)를 등록된 프로그램과
     매칭 → 기존 배포 큐(`DeployJobService.enqueue`)에 적재.
   - 인증 필터 예외 경로로 등록하되 서명 검증이 인증을 대체함을 SecurityConfig에 명시.
2. Gitea 조직 기본 webhook으로 설정해 신규 레포에 자동 적용.

**완료 기준(kind)** — 레포에 push → 수 초 내 DeployJob 적재 → 재배포 → 새 버전 응답.
서명 틀린 요청 401, main 외 브랜치 push는 무시되는 것 확인.

---

## 5단계 — 예제 미러링 + 문서 이관 (0.5일)

**작업**

1. `examples/` 7종을 Gitea `edu-examples` 조직 레포로 push하는 스크립트
   (`deploy/gitea-seed.sh`) 작성 — 재실행 안전(force-with-lease 없이 재생성 방식).
2. 시드 프로그램의 repo 주소를 `local://examples/<slug>` → Gitea 주소로 전환하는
   경로 마련(1차: 문서화된 수동 절차, 2차: 시드 로직 옵션).
3. 문서 이관 — `docs/VIBE_CODING_GUIDE.md`·등록 가이드의 "GitHub 레포" 안내를
   "내부 Gitea(권장) / 외부 GitHub(병용)"으로 갱신. `AI_BUILD_SPEC` 다운로드
   지시서의 업로드 안내도 동일 갱신.

**완료 기준** — Gitea 웹에서 예제 7종 소스 열람 가능, 프로그램 상세의
"Gitea 저장소" 링크가 실제 레포로 연결.

---

## 6단계 — 통합 검증 + 릴리스 (0.5~1일)

**검증 시나리오(kind, end-to-end)**

1. `bootstrap.sh up` 클린 설치에 Gitea 포함 자동 기동.
2. Gitea에 새 레포 생성 → 규격에 맞는 코드 push → 플랫폼 등록 → 관리자 승인 →
   자동 배포 → `https://<slug>.<DOMAIN>` 200.
3. 코드 수정 push → webhook 재배포 → 변경 반영 확인.
4. 장애 시나리오: Gitea 파드 재시작 후 데이터(레포·계정) 유지 확인(PVC).
5. 백업 리허설: `gitea dump` 크론 또는 PVC 스냅샷 절차 1회 실행·복원 확인.

**릴리스** — 서브에이전트(또는 동료) 리뷰 PASS → ROADMAP/PROCESS/VERSIONS 갱신 →
`v0.7.0` 태깅. ([VERSIONS.md](../VERSIONS.md) §5 프로세스 준수)

---

## 7단계(2차) — SSO 계정 연동 (별도 3~5일)

auth-service는 자체 HS256 JWT 발급기로 **OIDC Provider가 아니다.** Gitea 로그인
위임에는 다음 중 하나가 필요하며, 1차 범위에서는 **수동 계정 발급**으로 운영한다.

- **A안(권장)**: auth-service에 OIDC Provider 최소 구현(authorize/token/jwks/userinfo)
  추가 → Gitea OAuth2 소스로 등록. 교육청 SSO 연동(백로그 B)과 방향이 같다.
- **B안**: Keycloak 등 IdP를 중간에 도입 — 운영 컴포넌트가 하나 늘어나는 대신 표준적.

완료 기준: 포털 계정으로 Gitea 로그인, 권한(USER/CODER/ADMIN)→Gitea 권한 매핑.

---

## 리스크·운영 유의점

| 리스크 | 대응 |
|---|---|
| 레포 데이터 유실 | PVC 스냅샷 + `gitea dump` 정기 백업(6단계에서 리허설 필수) |
| 토큰 유출 | 봇 토큰은 read-only 최소 권한, Secret 관리, 주기 회전(백로그 A: Vault/Sealed Secrets와 연계) |
| webhook 위·변조 | HMAC 서명 검증 + main 브랜치 한정 + 등록된 프로그램만 매칭 |
| 대용량 레포 push 실패 | ingress `proxy-body-size` 상향, Gitea 레포 크기 제한 설정 |
| 차트 업그레이드 호환 | values.yaml 최소화 + 업그레이드 전 kind 리허설 |

## 진행 기록

- 2026-09-01 — 계획 수립. (진행 시 단계별 완료 일자·검증 결과를 여기에 추가)
