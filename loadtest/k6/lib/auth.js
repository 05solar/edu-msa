import http from 'k6/http';
import { check } from 'k6';
import { AUTH_URL, PASSWORD, ACCOUNTS, accountName } from './config.js';

// VU 로컬 토큰 캐시 — access 30분이라 테스트(수 분) 동안 재로그인 불필요.
let cached = null;

/** VU 마다 고유 시드 계정으로 로그인해 { token, name } 을 반환(캐시). */
export function ensureLogin() {
  if (cached) return cached;
  const username = accountName(((__VU - 1) % ACCOUNTS) + 1);
  const res = http.post(`${AUTH_URL}/api/auth/login`,
      JSON.stringify({ username, password: PASSWORD }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'auth_login' } });
  check(res, { 'login 200': (r) => r.status === 200 });
  if (res.status !== 200) return null;
  const body = res.json();
  cached = { token: body.accessToken, name: body.account.name, username };
  return cached;
}

export function authHeaders(session) {
  return { headers: { Authorization: `Bearer ${session.token}` } };
}
