// 대표 사용자 행동 혼합 시나리오 — 프론트엔드 실사용 패턴 기반 비중.
//   (로그인 직후 AppContext 가 목록+counts+알림을 로드하고, 탐색 페이지는 서버 검색/페이지네이션,
//    상세 열람, 30분 토큰 만료 전 refresh, 관리자 배포는 저빈도)
//
// 실행:  k6 run -e PROFILE=rps1000 -e BASE_URL=http://edu.localhost loadtest/k6/mixed.js \
//          --summary-export loadtest/results/<날짜>-rps1000-summary.json
// 비중(목표 RPS 대비): list 35% · search 20% · detail 20% · counts 10% · noti 10% · refresh 4% · login 1%
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, AUTH_URL, PASSWORD, ACCOUNTS, DURATION, PROFILE, SUMMARY_TREND_STATS,
         rpsScenario, accountName } from './lib/config.js';
import { ensureLogin, authHeaders } from './lib/auth.js';

export const options = {
  summaryTrendStats: SUMMARY_TREND_STATS,
  // 브라우저처럼 VU 쿠키(edu_refresh)를 iteration 간 유지 — 기본값(리셋)이면 refresh 회전
  // 체인이 끊겨 401 누적 → refresh IP 차단(429)이 발동한다(방어 정책의 정상 동작).
  noCookiesReset: true,
  // baseline 측정용 기준선 — 초과해도 중단하지 않고 기록만 남긴다(abortOnFail 없음)
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:catalog_list}': ['p(95)<300'],
    'http_req_duration{name:auth_login}': ['p(95)<1000'],
  },
  scenarios: {
    catalog_list: rpsScenario('catalogList', 35),
    catalog_search: rpsScenario('catalogSearch', 20),
    program_detail: rpsScenario('programDetail', 20),
    catalog_counts: rpsScenario('catalogCounts', 10),
    notifications: rpsScenario('notifications', 10),
    token_refresh: rpsScenario('tokenRefresh', 4),
    steady_login: rpsScenario('steadyLogin', 1),
  },
};

const SORTS = ['latest', 'popular', 'downloads'];
const CATS = ['all', 'doc', 'student', 'curri', 'budget', 'facil', 'data', 'civil'];
const QUERIES = ['검사', '엑셀', '생성', '통계', 'ocr', '계산'];

// setup: 상세 조회에 쓸 실제 프로그램 id 풀 확보(시드 데이터 기준)
export function setup() {
  const login = http.post(`${AUTH_URL}/api/auth/login`,
      JSON.stringify({ username: accountName(1), password: PASSWORD }),
      { headers: { 'Content-Type': 'application/json' } });
  if (login.status !== 200) throw new Error(`setup 로그인 실패(${login.status}) — 시드 계정을 먼저 준비하세요`);
  const token = login.json().accessToken;
  const list = http.get(`${BASE_URL}/api/programs?page=0&size=100`,
      { headers: { Authorization: `Bearer ${token}` } });
  const ids = list.status === 200 ? list.json().items.map((p) => p.id) : [];
  if (ids.length === 0) throw new Error('공개 프로그램이 없습니다 — seed/seed-programs.sql 을 먼저 적용하세요');
  return { ids };
}

function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

export function catalogList() {
  const s = ensureLogin(); if (!s) return;
  const page = Math.floor(Math.random() * 3);
  const res = http.get(`${BASE_URL}/api/programs?page=${page}&size=20&sort=${pick(SORTS)}`,
      Object.assign({ tags: { name: 'catalog_list' } }, authHeaders(s)));
  check(res, { 'list 200': (r) => r.status === 200 });
}

export function catalogSearch() {
  const s = ensureLogin(); if (!s) return;
  const cat = pick(CATS);
  const q = Math.random() < 0.5 ? `&q=${encodeURIComponent(pick(QUERIES))}` : '';
  const res = http.get(`${BASE_URL}/api/programs?page=0&size=20&cat=${cat}${q}`,
      Object.assign({ tags: { name: 'catalog_search' } }, authHeaders(s)));
  check(res, { 'search 200': (r) => r.status === 200 });
}

export function catalogCounts() {
  const s = ensureLogin(); if (!s) return;
  const res = http.get(`${BASE_URL}/api/programs/counts`,
      Object.assign({ tags: { name: 'catalog_counts' } }, authHeaders(s)));
  check(res, { 'counts 200': (r) => r.status === 200 });
}

export function programDetail(data) {
  const s = ensureLogin(); if (!s) return;
  const id = pick(data.ids);
  const res = http.get(`${BASE_URL}/api/programs/${id}`,
      Object.assign({ tags: { name: 'program_detail' } }, authHeaders(s)));
  check(res, { 'detail 200': (r) => r.status === 200 });
}

export function notifications() {
  const s = ensureLogin(); if (!s) return;
  const to = encodeURIComponent(s.name);
  const r1 = http.get(`${BASE_URL}/api/notifications?to=${to}`,
      Object.assign({ tags: { name: 'noti_list' } }, authHeaders(s)));
  const r2 = http.get(`${BASE_URL}/api/notifications/unread-count?to=${to}`,
      Object.assign({ tags: { name: 'noti_unread' } }, authHeaders(s)));
  check(r1, { 'noti 200': (r) => r.status === 200 });
  check(r2, { 'unread 200': (r) => r.status === 200 });
}

export function tokenRefresh() {
  const s = ensureLogin(); if (!s) return;   // 로그인 시 VU 쿠키 항아리에 edu_refresh 저장됨
  const res = http.post(`${AUTH_URL}/api/auth/refresh`, null, { tags: { name: 'auth_refresh' } });
  check(res, { 'refresh 200': (r) => r.status === 200 });
  if (res.status === 200) s.token = res.json().accessToken;   // 회전된 토큰 반영
}

// 정상 사용 중 저율 신규 로그인(캐시 미사용 — 매번 실제 로그인 경로)
export function steadyLogin() {
  const username = accountName(Math.floor(Math.random() * ACCOUNTS) + 1);
  const res = http.post(`${AUTH_URL}/api/auth/login`,
      JSON.stringify({ username, password: PASSWORD }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'auth_login' } });
  check(res, { 'login 200': (r) => r.status === 200 });
}

// 결과 저장: summary-export 외에 사람이 읽을 한 줄 기록도 남긴다
export function handleSummary(data) {
  const m = data.metrics.http_req_duration?.values || {};
  const fail = data.metrics.http_req_failed?.values?.rate ?? 0;
  const reqs = data.metrics.http_reqs?.values?.rate ?? 0;
  const line = `profile=${__ENV.PROFILE || 'rps100'} rps=${reqs.toFixed(0)} ` +
      `p50=${(m['p(50)'] || 0).toFixed(0)}ms p95=${(m['p(95)'] || 0).toFixed(0)}ms ` +
      `p99=${(m['p(99)'] || 0).toFixed(0)}ms err=${(fail * 100).toFixed(2)}%\n`;
  return { stdout: line };
}
