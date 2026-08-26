/*
 * 인증 폼 클라이언트 검증 규칙.
 * 2단계에서 auth-service 서버 검증과 동일한 규칙을 사용한다.
 * 각 함수는 통과 시 null, 실패 시 사용자에게 보여줄 메시지를 반환한다.
 */

export const USERNAME_RE = /^[a-z][a-z0-9_]{3,19}$/
export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[a-zA-Z]{2,}$/
export const PHONE_RE = /^01[016-9]-?\d{3,4}-?\d{4}$/

export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 64

export function required(value: string, label: string): string | null {
  return value.trim() ? null : `${label}을(를) 입력해 주세요.`
}

export function checkUsername(value: string): string | null {
  const v = value.trim()
  if (!v) return '아이디를 입력해 주세요.'
  if (!USERNAME_RE.test(v)) {
    return '아이디는 영문 소문자로 시작하는 4~20자이며, 영문 소문자·숫자·밑줄(_)만 사용합니다.'
  }
  return null
}

export function checkEmail(value: string): string | null {
  const v = value.trim()
  if (!v) return '이메일을 입력해 주세요.'
  if (!EMAIL_RE.test(v)) return '이메일 형식이 올바르지 않습니다.'
  return null
}

export function checkPhone(value: string): string | null {
  const v = value.trim()
  if (!v) return '휴대폰 번호를 입력해 주세요.'
  if (!PHONE_RE.test(v)) return '휴대폰 번호 형식이 올바르지 않습니다. (예: 010-1234-5678)'
  return null
}

/* 비밀번호 복잡도 — 개별 조건을 화면에서 체크리스트로 노출한다. */
export interface PasswordRule { key: string; label: string; ok: boolean }

export function passwordRules(value: string): PasswordRule[] {
  return [
    { key: 'len', label: `${PASSWORD_MIN}자 이상 ${PASSWORD_MAX}자 이하`, ok: value.length >= PASSWORD_MIN && value.length <= PASSWORD_MAX },
    { key: 'alpha', label: '영문 포함', ok: /[a-zA-Z]/.test(value) },
    { key: 'digit', label: '숫자 포함', ok: /\d/.test(value) },
    { key: 'special', label: '특수문자 포함', ok: /[^a-zA-Z0-9\s]/.test(value) },
  ]
}

export function checkPassword(value: string): string | null {
  if (!value) return '비밀번호를 입력해 주세요.'
  const failed = passwordRules(value).filter((r) => !r.ok)
  if (failed.length) return '비밀번호 조건을 모두 만족해야 합니다.'
  return null
}

export function checkPasswordConfirm(value: string, password: string): string | null {
  if (!value) return '비밀번호 확인을 입력해 주세요.'
  if (value !== password) return '비밀번호가 일치하지 않습니다.'
  return null
}

/* 폼 전체 검증 결과 헬퍼 — 값이 있는 항목만 오류로 모은다. */
export type Errors<K extends string> = Partial<Record<K, string>>

export function collect<K extends string>(entries: Array<[K, string | null]>): Errors<K> {
  const out: Errors<K> = {}
  for (const [key, msg] of entries) if (msg) out[key] = msg
  return out
}

export function hasError<K extends string>(errors: Errors<K>): boolean {
  return Object.keys(errors).length > 0
}
