// 배포 요청 시나리오(저빈도) — 큐 적재(202)와 워커 처리를 관측한다.
// 반드시 EDU_DEPLOY_MODE=simulate 환경에서만 실행한다(실제 빌드/기동 방지).
//
// 실행:  k6 run -e ADMIN_USER=<관리자ID> -e ADMIN_PASS=<비밀번호> \
//          -e BASE_URL=http://edu.localhost loadtest/k6/deploy-flow.js
//   DEPLOYS_PER_MIN  분당 배포 요청 수 (기본 6)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, SUMMARY_TREND_STATS } from './lib/config.js';

const PER_MIN = Number(__ENV.DEPLOYS_PER_MIN || 6);

export const options = {
  summaryTrendStats: SUMMARY_TREND_STATS,
  scenarios: {
    deploy_requests: {
      executor: 'constant-arrival-rate',
      exec: 'requestDeploy',
      rate: PER_MIN,
      timeUnit: '1m',
      duration: __ENV.DURATION || '5m',
      preAllocatedVUs: 5,
      maxVUs: 20,
    },
  },
};

export function setup() {
  const res = http.post(`${BASE_URL}/api/auth/login`,
      JSON.stringify({ username: __ENV.ADMIN_USER, password: __ENV.ADMIN_PASS }),
      { headers: { 'Content-Type': 'application/json' } });
  if (res.status !== 200) throw new Error(`관리자 로그인 실패(${res.status}) — ADMIN_USER/ADMIN_PASS 확인`);
  const token = res.json().accessToken;
  // P2-3: ad-hoc(/api/deploy) 제거 — 배포는 항상 프로그램 기반이므로 측정용 프로그램을 만든다.
  const prog = http.post(`${BASE_URL}/api/programs`,
      JSON.stringify({ name: 'loadtest-deploy-flow', summary: 'k6', cat: 'etc',
        repo: 'sample://test-code', branch: 'main' }),
      { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } });
  if (prog.status !== 201) throw new Error(`측정용 프로그램 생성 실패(${prog.status})`);
  return { token, programId: prog.json().id };
}

export function requestDeploy(data) {
  // 큐 적재(202) — 워커 처리량은 /api/deploy/jobs 로 병행 관측한다.
  // 같은 프로그램의 active 작업이 있으면 기존 작업 반환(멱등 — 역시 202).
  const res = http.post(`${BASE_URL}/api/programs/${data.programId}/deploy`,
      JSON.stringify({ programId: data.programId, repoUrl: 'sample://test-code', branch: 'main', actor: 'loadtest' }),
      { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` },
        tags: { name: 'deploy_enqueue' } });
  check(res, { 'deploy 202': (r) => r.status === 202 });
  sleep(1);
  const jobs = http.get(`${BASE_URL}/api/deploy/jobs`,
      { headers: { Authorization: `Bearer ${data.token}` }, tags: { name: 'deploy_jobs' } });
  check(jobs, { 'jobs 200': (r) => r.status === 200 });
}
