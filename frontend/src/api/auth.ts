/*
 * auth-service 클라이언트.
 * 플랫폼 API(api/client.ts)와 달리 VITE_USE_API 플래그와 무관하게 항상 실제 서버를 호출한다.
 * (데모 로그인은 서버를 거치지 않는 별도 흐름으로 유지된다)
 *
 * 개발 서버는 vite.config.ts 프록시로 /api/auth → auth-service 로 전달한다.
 */
import { authHeader } from './token'
import type { Role } from '../types'

const BASE = '/api/auth'

export interface AuthAccount {
  id: number
  username: string
  name: string
  email: string
  dept: string
  role: Role
  mustChangePassword: boolean
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  account: AuthAccount
}

export interface SignupInput {
  username: string
  password: string
  name: string
  email: string
  dept: string
}

export type DuplicateField = 'username' | 'email'

export interface DuplicateResponse {
  field: DuplicateField
  value: string
  available: boolean
}

/** 서버가 내려주는 오류 본문을 그대로 메시지로 노출하기 위한 예외. */
export class AuthApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'AuthApiError'
    this.status = status
    this.code = code
  }
}

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + path, {
    // Refresh Token 이 HttpOnly 쿠키로 오가므로 자격 증명을 포함해야 한다.
    credentials: 'include',
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...authHeader(),
      ...(init?.headers as Record<string, string> | undefined),
    },
  })

  if (!res.ok) {
    let code = 'ERROR'
    let message = `요청에 실패했습니다. (${res.status})`
    try {
      const body = await res.json()
      code = body.code ?? code
      message = body.message ?? message
    } catch {
      /* 본문이 비어 있거나 JSON 이 아닌 경우 기본 메시지를 쓴다 */
    }
    throw new AuthApiError(res.status, code, message)
  }

  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export const authApi = {
  signup: (input: SignupInput) =>
    req<AuthAccount>('/signup', { method: 'POST', body: JSON.stringify(input) }),

  login: (username: string, password: string) =>
    req<TokenResponse>('/login', { method: 'POST', body: JSON.stringify({ username, password }) }),

  /**
   * 시연용 데모 로그인 — 비밀번호 없이 역할별 데모 계정의 토큰을 받는다.
   * 발급되는 토큰은 일반 로그인과 같으므로 플랫폼 API 도 그대로 사용할 수 있다.
   */
  demoLogin: (role: Role) =>
    req<TokenResponse>('/demo-login', { method: 'POST', body: JSON.stringify({ role }) }),

  /** Refresh 쿠키로 Access Token 을 재발급한다. 세션이 없으면 401. */
  refresh: () => req<TokenResponse>('/refresh', { method: 'POST' }),

  logout: () => req<{ message: string }>('/logout', { method: 'POST' }),

  me: () => req<AuthAccount>('/me'),

  checkDuplicate: (field: DuplicateField, value: string) =>
    req<DuplicateResponse>(`/check-duplicate?field=${field}&value=${encodeURIComponent(value)}`),

  /** 운영 관리자 전용 — 계정 목록. 권한 판단의 기준은 auth-service 이다. */
  accounts: () => req<AuthAccount[]>('/accounts'),

  /** 운영 관리자 전용 — 권한 부여. */
  setRole: (username: string, role: Role) =>
    req<AuthAccount>(`/accounts/${encodeURIComponent(username)}/role`,
      { method: 'PATCH', body: JSON.stringify({ role }) }),
}
