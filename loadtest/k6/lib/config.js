// 부하 프로파일·공통 설정. 모든 값은 환경변수로 재정의한다.
//   BASE_URL   플랫폼 backend (기본 http://edu.localhost)
//   PROFILE    rps100 | rps500 | rps1000 | rps2000  (1차 기준: rps1000 = 동접 1만 규모)
//   DURATION   steady 구간 길이 (기본 5m)
//   ACCOUNTS   시드된 테스트 계정 수 (기본 10000, seed/README 참조)
export const BASE_URL = __ENV.BASE_URL || 'http://edu.localhost';
// 인증 엔드포인트 베이스 — 단일 인그레스면 BASE_URL 과 같고,
// 로컬(포트 분리: backend :8088 / auth :8089)은 AUTH_URL 로 재정의한다.
export const AUTH_URL = __ENV.AUTH_URL || BASE_URL;
export const PASSWORD = __ENV.LT_PASSWORD || 'LoadTest#2026!';
export const ACCOUNTS = Number(__ENV.ACCOUNTS || 10000);
export const DURATION = __ENV.DURATION || '5m';

// 단계별 목표 RPS — preAllocatedVUs 는 "RPS × 예상 p95(초) × 여유 2배" 기준의 시작값.
const PROFILES = {
  rps100:  { rate: 100,  preAllocatedVUs: 100,  maxVUs: 400 },
  rps500:  { rate: 500,  preAllocatedVUs: 400,  maxVUs: 1500 },
  rps1000: { rate: 1000, preAllocatedVUs: 800,  maxVUs: 3000 },
  rps2000: { rate: 2000, preAllocatedVUs: 1500, maxVUs: 6000 },
};
export const PROFILE = PROFILES[__ENV.PROFILE || 'rps100'];

// p50/p95/p99 를 요약에 포함
export const SUMMARY_TREND_STATS = ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'];

/** 목표 RPS 를 시나리오 비중(%)만큼 나눠 constant-arrival-rate 시나리오를 만든다. */
export function rpsScenario(exec, sharePercent, extra = {}) {
  const rate = Math.max(1, Math.round((PROFILE.rate * sharePercent) / 100));
  return {
    executor: 'constant-arrival-rate',
    exec,
    rate,
    timeUnit: '1s',
    duration: DURATION,
    preAllocatedVUs: Math.max(10, Math.round((PROFILE.preAllocatedVUs * sharePercent) / 100)),
    maxVUs: Math.max(20, Math.round((PROFILE.maxVUs * sharePercent) / 100)),
    ...extra,
  };
}

/** n번째 테스트 계정의 아이디 (seed/seed-accounts.sql 과 규칙 일치) */
export function accountName(i) {
  return `lt_user_${String(i).padStart(6, '0')}`;
}
