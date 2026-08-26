/*
 * 백엔드 API 클라이언트. VITE_USE_API=true 일 때 AppContext가 이 클라이언트를 사용한다.
 * (미설정 시 프론트엔드는 목업 데이터로 동작하는 오프라인 데모 모드)
 * 개발 서버는 vite.config.ts 의 프록시로 /api → 백엔드로 전달한다.
 */
import { authHeader } from './token'
import type {
  AdminLogEntry, AppUser, Comment, Notification, Program, Role, PurposeId, RunTypeId, Scope,
} from '../types'

// 기본값: API 연동 사용. 명시적으로 VITE_USE_API=false 일 때만 목업(오프라인 데모) 모드.
export const USE_API = import.meta.env.VITE_USE_API !== 'false'
const BASE = '/api'

export interface SpecView {
  name: string; slug: string; category: string
  purposes: string[]; tech: string[]; summary: string; port: number; health: string
}
export interface ValidationResult {
  valid: boolean; errors: string[]; spec: SpecView | null; resolvedFrom: string
}
export interface DeploymentResponse {
  id: number; programId: number | null; slug: string; name: string
  status: string; url: string | null; imageTag: string | null; mode: string
  manifest: string | null; log: string | null; createdAt: string
}

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...authHeader(),
      ...(init?.headers as Record<string, string> | undefined),
    },
  })
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`
    try { const b = await res.json(); if (b?.message) msg = b.message } catch { /* ignore */ }
    throw new Error(msg)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/* ---- 백엔드 응답 → 프론트엔드 Program 매핑 ---- */
interface SummaryDto {
  id: number; name: string; cat: string; owner: string; dept: string; ver: string
  updated: string; created: string; branch: string; repo: string; summary: string
  tags: string[]; purposes: string[]; tech: string[]; run: string[]
  views: number; likes: number; downloads: number; status: string; scope: string
}
interface DetailDto extends SummaryDto {
  desc: string; rejectReason: string | null; stopReason: string | null
  features: string[]; readme: string[]
  history: { ver: string; date: string; log: string }[]
  files: { name: string; size: string; type: string }[]
  comments: Comment[]
}

function fromSummary(d: SummaryDto): Program {
  return {
    id: d.id, name: d.name, cat: d.cat as Program['cat'], owner: d.owner, dept: d.dept,
    ver: d.ver, updated: d.updated, created: d.created, branch: d.branch,
    tags: d.tags, purposes: d.purposes as PurposeId[], tech: d.tech, run: d.run as RunTypeId[],
    history: [], summary: d.summary, desc: d.summary, repo: d.repo,
    views: d.views, likes: d.likes, downloads: d.downloads,
    status: d.status as Program['status'], scope: d.scope as Scope,
    files: [], readme: [], comments: [],
  }
}
function fromDetail(d: DetailDto): Program {
  return {
    ...fromSummary(d),
    desc: d.desc, rejectReason: d.rejectReason ?? undefined, stopReason: d.stopReason ?? undefined,
    features: d.features, readme: d.readme, history: d.history, files: d.files, comments: d.comments,
  }
}

export const api = {
  // 공개 카탈로그(역할 필터·PUBLIC) — 로그인한 모든 사용자 접근 가능
  list: () => req<SummaryDto[]>('/programs').then((xs) => xs.map(fromSummary)),
  // 전체(대기·비공개 포함) — 운영 관리자(ADMIN) 전용
  listAll: () => req<SummaryDto[]>('/programs/all').then((xs) => xs.map(fromSummary)),
  detail: (id: number) => req<DetailDto>(`/programs/${id}`).then(fromDetail),
  create: (body: {
    name: string; summary: string; desc?: string; cat: string; owner: string; dept: string
    ver?: string; repo: string; branch?: string; tags: string[]; purposes: PurposeId[]
    run: RunTypeId[]; scope: Scope; readme?: string
  }) => req<DetailDto>('/programs', { method: 'POST', body: JSON.stringify(body) }).then(fromDetail),
  review: (id: number, action: 'approve' | 'reject' | 'stop' | 'resume', memo: string, actor: string) =>
    req<void>(`/programs/${id}/review`, { method: 'POST', body: JSON.stringify({ action, memo, actor }) }),
  addComment: (id: number, body: { user: string; dept: string; body: string }) =>
    req<Comment>(`/programs/${id}/comments`, { method: 'POST', body: JSON.stringify(body) }),

  notifications: (to: string) => req<Notification[]>(`/notifications?to=${encodeURIComponent(to)}`),
  readNoti: (id: number) => req<void>(`/notifications/${id}/read`, { method: 'POST' }),
  readAllNotis: (to: string) => req<void>(`/notifications/read-all?to=${encodeURIComponent(to)}`, { method: 'POST' }),

  reviewLogs: () => req<AdminLogEntry[]>('/review/logs'),

  validateSpec: (repoUrl: string, branch?: string) =>
    req<ValidationResult>('/deploy/validate', { method: 'POST', body: JSON.stringify({ repoUrl, branch }) }),
  deployProgram: (id: number, repoUrl: string, branch: string, actor: string) =>
    req<DeploymentResponse>(`/programs/${id}/deploy`, { method: 'POST', body: JSON.stringify({ repoUrl, branch, actor }) }),
  deploymentOf: (id: number) => req<DeploymentResponse | null>(`/programs/${id}/deployment`),

  users: () => req<AppUser[]>('/users'),
  setRole: (name: string, role: Role) =>
    req<AppUser>(`/users/${encodeURIComponent(name)}/role`, { method: 'PATCH', body: JSON.stringify({ role }) }),
}
