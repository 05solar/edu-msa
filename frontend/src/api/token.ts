/*
 * 로그인 세션의 Access Token 보관소.
 *
 * XSS 로 탈취되지 않도록 localStorage 대신 메모리에만 둔다.
 * 새로고침 시에는 HttpOnly Refresh 쿠키로 POST /api/auth/refresh 를 호출해 복구한다.
 */
let accessToken: string | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

/** Authorization 헤더 — 토큰이 없으면 빈 객체를 돌려준다. */
export function authHeader(): Record<string, string> {
  return accessToken ? { Authorization: `Bearer ${accessToken}` } : {}
}
