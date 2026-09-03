#!/usr/bin/env bash
# =============================================================================
# 예제 서비스 7종을 내부 Gitea 로 미러링한다. (GITEA_PLAN §5)
#
#   기본(kind):   ./deploy/gitea-seed.sh
#   실서버:       GITEA_URL=https://gitea.edu.internal INSECURE=1 ./deploy/gitea-seed.sh
#
# 하는 일: edu-examples 조직 생성 → 예제별 레포 재생성(있으면 삭제 후) → git push.
# 재실행 안전: 레포를 삭제 후 재생성하므로 force-with-lease 없이 항상 동일 결과.
# 자격: GITEA_ADMIN_USER/GITEA_ADMIN_PASSWORD 지정 또는 kind Secret(gitea-admin)에서 읽음.
# =============================================================================
set -euo pipefail

GITEA_URL="${GITEA_URL:-http://gitea.localhost}"
ORG="${GITEA_ORG:-edu-examples}"
ADMIN_USER="${GITEA_ADMIN_USER:-edu-admin}"
ADMIN_PASS="${GITEA_ADMIN_PASSWORD:-}"
INSECURE="${INSECURE:-0}"       # 1 = 자체 CA(TLS) 검증 생략 (curl -k, git sslVerify=false)

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SLUGS=(doc-proofreader seat-maker timetable-checker travel-allowance asset-label data-summarizer doc-ocr)

c_i='\033[1;36m'; c_ok='\033[1;32m'; c_e='\033[1;31m'; c_0='\033[0m'
log(){ printf "${c_i}▶ %s${c_0}\n" "$*"; }
ok(){  printf "${c_ok}✓ %s${c_0}\n" "$*"; }
die(){ printf "${c_e}✗ %s${c_0}\n" "$*" >&2; exit 1; }

command -v git >/dev/null || die "git 이 필요합니다."
command -v curl >/dev/null || die "curl 이 필요합니다."

# 관리자 비밀번호 — 미지정 시 클러스터 Secret 에서 읽는다(kind/실서버 공통 배치).
if [ -z "$ADMIN_PASS" ] && command -v kubectl >/dev/null 2>&1; then
  ADMIN_PASS="$(kubectl -n gitea get secret gitea-admin -o jsonpath='{.data.password}' 2>/dev/null | base64 -d || true)"
fi
[ -n "$ADMIN_PASS" ] || die "GITEA_ADMIN_PASSWORD 를 지정하거나 kubectl 로 gitea-admin Secret 에 접근 가능해야 합니다."

CURL_OPTS=(-s -u "${ADMIN_USER}:${ADMIN_PASS}")
[ "$INSECURE" = 1 ] && CURL_OPTS+=(-k)
api(){ local path="$1"; shift; curl "${CURL_OPTS[@]}" "${GITEA_URL}/api/v1${path}" "$@"; }

# git 자격 증명은 URL/인자에 넣지 않고 extraHeader 환경변수로 주입한다(프로세스 목록 비노출).
GIT_B64="$(printf '%s:%s' "$ADMIN_USER" "$ADMIN_PASS" | base64 | tr -d '\n')"
git_push_env(){
  env GIT_TERMINAL_PROMPT=0 \
      GIT_CONFIG_COUNT=1 \
      GIT_CONFIG_KEY_0="http.${GITEA_URL}/.extraHeader" \
      GIT_CONFIG_VALUE_0="Authorization: Basic ${GIT_B64}" \
      git "$@"
}
[ "$INSECURE" = 1 ] && git_config_ssl=(-c http.sslVerify=false) || git_config_ssl=()

log "Gitea 연결 확인: ${GITEA_URL}"
api "/version" >/dev/null || die "Gitea API 에 접근할 수 없습니다: ${GITEA_URL}"

# 1) 조직 준비 (예제는 열람용이므로 공개)
if ! api "/orgs/${ORG}" -o /dev/null -w '%{http_code}' | grep -q '^200$'; then
  log "조직 생성: ${ORG}"
  # 설명은 ASCII 만 쓴다 — Windows Git Bash 등 비 UTF-8 로캘 셸에서 한글이 CP949 로
  # 전송되어 Gitea 가 422(invalid UTF-8)로 거부하는 문제를 피한다.
  api "/orgs" -H 'Content-Type: application/json' -X POST \
    -d "{\"username\":\"${ORG}\",\"visibility\":\"public\",\"description\":\"edu-msa example services\"}" \
    -o /dev/null
fi

# 2) 레포 재생성 + push
for s in "${SLUGS[@]}"; do
  src="$ROOT/examples/$s"
  [ -d "$src" ] || die "예제 폴더가 없습니다: $src"
  log "미러링: ${ORG}/${s}"
  api "/repos/${ORG}/${s}" -X DELETE -o /dev/null || true    # 있으면 삭제(재실행 안전)
  # 레포 설명(한글 summary)은 셸 인코딩 문제로 넣지 않는다 — 설명은 플랫폼 카탈로그가 담당.
  api "/orgs/${ORG}/repos" -H 'Content-Type: application/json' -X POST \
    -d "{\"name\":\"${s}\",\"private\":false,\"default_branch\":\"main\"}" \
    -o /dev/null

  tmp="$(mktemp -d)"
  cp -r "$src/." "$tmp/"
  ( cd "$tmp" \
    && git "${git_config_ssl[@]}" init -q -b main \
    && git add -A \
    && git -c user.email=platform@edu.local -c user.name=edu-msa commit -qm "예제 미러링: ${s}" \
    && git_push_env "${git_config_ssl[@]}" push -q "${GITEA_URL}/${ORG}/${s}.git" main )
  rm -rf "$tmp"
  ok "${GITEA_URL}/${ORG}/${s}"
done

ok "예제 ${#SLUGS[@]}종 미러링 완료 — 웹에서 확인: ${GITEA_URL}/${ORG}"
echo "시드 프로그램 주소 전환(1차 수동 절차)은 deploy/k8s/platform/gitea/README.md §5 참고."
