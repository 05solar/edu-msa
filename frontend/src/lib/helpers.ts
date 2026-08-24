import { CATEGORIES, PURPOSES, RUN_TYPES } from '../data/catalog'
import type { Category, Program, Purpose, PurposeId, RunType, RunTypeId } from '../types'

/* 숫자 천단위 콤마 */
export function num(n: number): string {
  return Number(n).toLocaleString('ko-KR')
}

/* 날짜 하이픈 → 점 */
export function dot(d: string): string {
  return String(d || '').replace(/-/g, '.')
}

/* 이름 앞 2글자 (아바타용) */
export function initialOf(name: string): string {
  return String(name || '').slice(0, 2)
}

/* 오늘(한국 시간) YYYY-MM-DD */
export function today(): string {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date())
}

/* 현재 시각 스탬프 */
export function nowStamp(): string {
  const t = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date())
  return dot(today()) + ' ' + t.replace(/[^0-9:]/g, '')
}

/* 분류 접근자 */
const FALLBACK_CAT: Category = { id: 'doc', name: '미분류', icon: 'folder', color: '#8A94A6' }
export function catOf(id: string): Category {
  return CATEGORIES.find((c) => c.id === id) ?? FALLBACK_CAT
}
export function purposeOf(id: PurposeId): Purpose {
  return PURPOSES.find((p) => p.id === id) ?? { id, name: id, icon: 'folder' }
}
export function runTypeOf(id: RunTypeId): RunType {
  return RUN_TYPES.find((r) => r.id === id) ?? { id, name: id, icon: 'folder', desc: '' }
}

export function purposesOf(p: Program): PurposeId[] {
  return p.purposes ?? []
}
export function techOf(p: Program): string[] {
  return p.tech?.length ? p.tech : (p.tags ?? [])
}
export function runOf(p: Program): RunTypeId[] {
  return p.run?.length ? p.run : ['gitea']
}
export function branchOf(p: Program): string {
  return p.branch ?? 'main'
}
export function featuresOf(p: Program): string[] {
  if (p.features?.length) return p.features
  // 기능 목록이 없으면 요약을 문장 단위로 나눠 임시 생성
  return (p.desc || p.summary || '')
    .split(/(?<=[.。])\s+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 4)
}

export function repoName(url: string): string {
  const m = String(url || '').replace(/\/+$/, '').split('/')
  return m[m.length - 1] || url
}

export function cloneCmd(p: Program): string {
  const b = branchOf(p)
  return `git clone -b ${b} ${p.repo}`
}

export function statusLabel(s: Program['status']): string {
  return { draft: '임시저장', pending: '승인 대기', public: '공개 중', rejected: '반려', stopped: '공개 중지' }[s]
}
export function statusBadgeClass(s: Program['status']): string {
  return {
    draft: 'badge-gray', pending: 'badge-warn', public: 'badge-ok',
    rejected: 'badge-danger', stopped: 'badge-stop',
  }[s]
}
