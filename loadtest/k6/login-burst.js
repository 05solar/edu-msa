// 로그인 폭주(burst) 시나리오 — 출근 시간대 동시 로그인을 흉내낸다.
// bcrypt CPU 포화 지점·auth-service HPA 반응·rate-limit(429) 발동 여부를 측정한다.
//
// 실행:  k6 run -e BURST_RPS=200 -e BASE_URL=http://edu.localhost loadtest/k6/login-burst.js \
//          --summary-export loadtest/results/<날짜>-login-burst-summary.json
//   BURST_RPS  피크 로그인 초당 횟수 (기본 100 — 30분에 10만 로그인 ≈ 55/s 의 약 2배)
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { AUTH_URL, PASSWORD, ACCOUNTS, SUMMARY_TREND_STATS, accountName } from './lib/config.js';

const BURST_RPS = Number(__ENV.BURST_RPS || 100);
const rateLimited = new Counter('login_rate_limited');   // 429 — 정책 발동 관측용(오류 아님)

export const options = {
  summaryTrendStats: SUMMARY_TREND_STATS,
  scenarios: {
    login_burst: {
      executor: 'ramping-arrival-rate',
      exec: 'burstLogin',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: BURST_RPS * 3,
      maxVUs: BURST_RPS * 10,
      stages: [
        { target: Math.round(BURST_RPS / 4), duration: '30s' },   // 예열
        { target: BURST_RPS, duration: '1m' },                    // 폭주 도달
        { target: BURST_RPS, duration: '3m' },                    // 피크 유지
        { target: 0, duration: '30s' },                           // 해소
      ],
    },
  },
  thresholds: {
    'http_req_duration{name:auth_login}': ['p(95)<2000'],
    // 429 는 방어 정책의 정상 동작 — 별도 카운터로만 관측한다
    'checks{name:auth_login}': ['rate>0.95'],
  },
};

let seq = 0;
export function burstLogin() {
  // VU·반복마다 서로 다른 계정 — 계정 잠금(같은 계정 연속 실패) 없이 순수 처리량을 잰다
  const idx = ((__VU * 7919 + seq++) % ACCOUNTS) + 1;
  const res = http.post(`${AUTH_URL}/api/auth/login`,
      JSON.stringify({ username: accountName(idx), password: PASSWORD }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'auth_login' } });
  if (res.status === 429) rateLimited.add(1);
  check(res, { 'login ok(200/429)': (r) => r.status === 200 || r.status === 429 },
      { name: 'auth_login' });
}
