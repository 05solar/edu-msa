// 스모크 — 본 측정 전에 시드/엔드포인트/인증 배선이 맞는지 1 VU 로 확인한다.
//   k6 run -e BASE_URL=http://edu.localhost loadtest/k6/smoke.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, AUTH_URL, PASSWORD, accountName } from './lib/config.js';

export const options = { vus: 1, iterations: 1 };

export default function () {
  const login = http.post(`${AUTH_URL}/api/auth/login`,
      JSON.stringify({ username: accountName(1), password: PASSWORD }),
      { headers: { 'Content-Type': 'application/json' } });
  check(login, { 'login 200': (r) => r.status === 200 });
  if (login.status !== 200) return;
  const token = login.json().accessToken;
  const h = { headers: { Authorization: `Bearer ${token}` } };

  check(http.get(`${BASE_URL}/api/programs?page=0&size=20`, h), { 'list 200': (r) => r.status === 200 });
  check(http.get(`${BASE_URL}/api/programs/counts`, h), { 'counts 200': (r) => r.status === 200 });
  check(http.post(`${AUTH_URL}/api/auth/refresh`, null), { 'refresh 200': (r) => r.status === 200 });
}
