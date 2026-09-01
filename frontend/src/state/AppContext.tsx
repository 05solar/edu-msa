import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
  type ReactNode,
} from 'react'
import { ROLE_USER } from '../data/catalog'
import { USE_API, api } from '../api/client'
import { setAccessToken } from '../api/token'
import { authApi, type AuthAccount, type TokenResponse } from '../api/auth'
import { ADMIN_LOG_SEED, NOTIS_SEED, PROGRAMS, USERS_SEED } from '../data/programs'
import { nowStamp, today, dot } from '../lib/helpers'
import type {
  AdminAction, AdminLogEntry, AppUser, AuthView, Comment, NotiKind, Notification, Program,
  ProgramStatus, PurposeId, Role, Scope, ViewId,
} from '../types'

export interface Filters {
  q: string
  cat: 'all' | string
  purposes: PurposeId[]
  tech: string[]
  scope: 'any' | Scope
  sort: 'latest' | 'popular' | 'downloads'
}

export interface Toast { id: number; msg: string; kind: 'ok' | 'warn' | 'info' }

export type FontScale = 'sm' | 'md' | 'lg' | 'xl' | 'xxl' | 'xxxl' | 'x4' | 'x5'
export const FONT_SCALES: FontScale[] = ['sm', 'md', 'lg', 'xl', 'xxl', 'xxxl', 'x4', 'x5']
const FONT_ZOOM: Record<FontScale, string> = {
  sm: '1', md: '1.08', lg: '1.22', xl: '1.4', xxl: '1.62', xxxl: '1.9', x4: '2.25', x5: '2.6',
}

export interface NewProgramInput {
  name: string; summary: string; desc: string; cat: string; dept: string
  ver: string; repo: string; branch: string; tags: string[]
  purposes: PurposeId[]; run: Program['run']; scope: Scope; readme: string
}

interface AppContextValue {
  loggedIn: boolean
  login: () => void
  logout: () => void

  authView: AuthView
  goAuth: (v: AuthView) => void

  /** auth-service 로 로그인한 계정. 데모 로그인 상태에서는 null. */
  account: AuthAccount | null
  /** 시연용 데모 진입 여부 — 권한 전환 등 데모 전용 기능의 노출 기준. */
  demoMode: boolean
  /** 데모 로그인 진행 중 여부. */
  demoPending: boolean
  /** 새로고침 시 세션 복구 시도가 끝났는지 여부. */
  authReady: boolean
  signIn: (username: string, password: string) => Promise<void>
  /** 마이페이지에서 외부 사용자가 스스로 상향 권한을 신청/취소한다(승인은 운영 관리자). */
  requestRoleUpgrade: (requestRole: 'coder' | 'admin', reason?: string) => Promise<void>
  cancelRoleUpgrade: () => Promise<void>

  role: Role
  changeRole: (r: Role) => void
  me: AppUser

  view: ViewId
  detailId: number | null
  go: (view: ViewId, id?: number) => void

  sideCollapsed: boolean
  toggleSide: () => void
  sidebarOpen: boolean
  setSidebarOpen: (v: boolean) => void

  theme: 'light' | 'dark'
  toggleTheme: () => void
  fontScale: FontScale
  setFontScale: (s: FontScale) => void

  myTab: 'mine' | 'fav' | 'noti'
  setMyTab: (t: 'mine' | 'fav' | 'noti') => void

  programs: Program[]
  progOf: (id: number) => Program | null
  publicPrograms: Program[]
  myPrograms: Program[]
  canSee: (p: Program) => boolean

  filters: Filters
  setFilters: (patch: Partial<Filters>) => void
  resetFilters: () => void

  favorites: number[]
  isFav: (id: number) => boolean
  toggleFav: (id: number) => void
  favPrograms: Program[]

  notis: Notification[]
  myNotis: Notification[]
  unreadCount: number
  readNoti: (id: number) => void
  readAllNotis: () => void

  adminLog: AdminLogEntry[]
  users: AppUser[]
  pendingPrograms: Program[]
  reviewProgram: (id: number, act: AdminAction, memo: string) => void
  setUserRole: (name: string, role: Role) => void

  addProgram: (input: NewProgramInput) => void
  redeployProgram: (id: number, version: string, note: string) => Promise<boolean>
  addComment: (id: number, body: { user: string; dept: string; body: string }) => void
  loadDetail: (id: number) => void

  toasts: Toast[]
  toast: (msg: string, kind?: Toast['kind']) => void
  dismissToast: (id: number) => void

  modal: ReactNode
  openModal: (node: ReactNode) => void
  closeModal: () => void
}

const AppContext = createContext<AppContextValue | null>(null)

const DEFAULT_FILTERS: Filters = {
  q: '', cat: 'all', purposes: [], tech: [], scope: 'any', sort: 'latest',
}

/** 데모 진입 여부를 새로고침 후에도 유지하기 위한 키. */
const DEMO_FLAG = 'edu-demo-mode'

const FAVORITES_SEED: Record<Role, number[]> = {
  user: [4, 5], coder: [2, 5], admin: [1, 4],
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [loggedIn, setLoggedIn] = useState(false)
  const [authView, setAuthView] = useState<AuthView>('login')
  const [account, setAccount] = useState<AuthAccount | null>(null)
  const [authReady, setAuthReady] = useState(false)
  const [accessExpiresIn, setAccessExpiresIn] = useState(0)
  const [authAccounts, setAuthAccounts] = useState<AuthAccount[]>([])
  // 데모 진입 여부는 표시용 플래그다(권한 전환 UI 노출 기준).
  // 실제 접근 권한은 서버가 토큰으로 판단하므로 브라우저에 두어도 무방하다.
  const [demoMode, setDemoMode] = useState(() => localStorage.getItem(DEMO_FLAG) === 'true')
  const [demoPending, setDemoPending] = useState(false)
  const [role, setRole] = useState<Role>('coder')
  const [view, setView] = useState<ViewId>('home')
  const [detailId, setDetailId] = useState<number | null>(null)
  const [sideCollapsed, setSideCollapsed] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [theme, setTheme] = useState<'light' | 'dark'>(() => {
    const saved = localStorage.getItem('edu-theme')
    if (saved === 'light' || saved === 'dark') return saved
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  })
  const [fontScale, setFontScaleState] = useState<FontScale>(() => {
    const s = localStorage.getItem('edu-fontscale')
    return (FONT_SCALES as string[]).includes(s ?? '') ? (s as FontScale) : 'md'
  })

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('edu-theme', theme)
  }, [theme])
  useEffect(() => {
    document.documentElement.style.setProperty('--ui-zoom', FONT_ZOOM[fontScale])
    localStorage.setItem('edu-fontscale', fontScale)
  }, [fontScale])

  const toggleTheme = useCallback(() => setTheme((t) => (t === 'light' ? 'dark' : 'light')), [])
  const setFontScale = useCallback((s: FontScale) => setFontScaleState(s), [])
  const [myTab, setMyTab] = useState<'mine' | 'fav' | 'noti'>('mine')

  const [programs, setPrograms] = useState<Program[]>(() => USE_API ? [] : PROGRAMS.map((p) => ({ ...p })))
  const [notis, setNotis] = useState<Notification[]>(() => USE_API ? [] : NOTIS_SEED.map((n) => ({ ...n })))
  const [adminLog, setAdminLog] = useState<AdminLogEntry[]>(() => ADMIN_LOG_SEED.map((a) => ({ ...a })))
  const [users, setUsers] = useState<AppUser[]>(() => USE_API ? [] : USERS_SEED.map((u) => ({ ...u })))
  const [favByRole, setFavByRole] = useState<Record<Role, number[]>>(FAVORITES_SEED)
  const [filters, setFiltersState] = useState<Filters>(DEFAULT_FILTERS)

  const [toasts, setToasts] = useState<Toast[]>([])
  const [modal, setModal] = useState<ReactNode>(null)
  const [, setSeq] = useState(1000)

  // 실제 로그인 상태에서는 auth-service 의 계정이 기준이고,
  // 데모 진입 상태에서는 기존과 동일하게 역할별 시연 사용자를 쓴다.
  const me = useMemo<AppUser>(
    () => (account
      ? { name: account.name, dept: account.dept, role: account.role }
      : ROLE_USER[role]),
    [account, role],
  )

  const toast = useCallback((msg: string, kind: Toast['kind'] = 'ok') => {
    setSeq((s) => {
      const id = s + 1
      setToasts((prev) => [...prev, { id, msg, kind }])
      window.setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id))
      }, 3200)
      return id
    })
  }, [])
  const dismissToast = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }, [])

  const openModal = useCallback((node: ReactNode) => setModal(node), [])
  const closeModal = useCallback(() => setModal(null), [])

  const applySession = useCallback((res: TokenResponse, demo: boolean) => {
    setAccessToken(res.accessToken)
    setAccount(res.account)
    setAccessExpiresIn(res.expiresIn)
    setRole(res.account.role)
    setDemoMode(demo)
    localStorage.setItem(DEMO_FLAG, String(demo))
    setLoggedIn(true)
  }, [])

  /**
   * 데모 로그인 — 시연용 진입 흐름.
   * 비밀번호 없이 역할별 데모 계정의 토큰을 받아오므로, 진입 후 플랫폼 API 도 그대로 쓸 수 있다.
   */
  const login = useCallback(() => {
    setDemoPending(true)
    authApi.demoLogin('coder')
      .then((res) => { applySession(res, true); setView('home') })
      .catch((e) => toast('데모 로그인에 실패했습니다: ' + (e as Error).message, 'warn'))
      .finally(() => setDemoPending(false))
  }, [applySession, toast])

  const clearSession = useCallback(() => {
    setAccount(null)
    setAuthAccounts([])
    setAccessToken(null)
    setDemoMode(false)
    localStorage.removeItem(DEMO_FLAG)
    setLoggedIn(false)
    setView('home')
    setDetailId(null)
    setAuthView('login')
  }, [])

  const logout = useCallback(() => {
    // 데모 진입이었다면 서버 세션이 없으므로 호출하지 않는다.
    if (account) authApi.logout().catch(() => { /* 이미 만료된 세션이어도 로그아웃은 진행한다 */ })
    clearSession()
  }, [account, clearSession])

  /** auth-service 로그인 — Access Token 은 메모리에, Refresh Token 은 HttpOnly 쿠키에 보관된다. */
  const signIn = useCallback(async (username: string, password: string) => {
    applySession(await authApi.login(username, password), false)
    setView('home')
  }, [applySession])

  // 마이페이지 — 외부 사용자가 스스로 상향 권한을 신청/취소한다. 계정 상태(account)를 갱신해 즉시 반영.
  const requestRoleUpgrade = useCallback(async (requestRole: 'coder' | 'admin', reason?: string) => {
    try {
      setAccount(await authApi.requestRoleUpgrade(requestRole, reason))
      toast('권한 신청이 접수되었습니다. 운영 관리자 승인 후 적용됩니다.', 'ok')
    } catch (e) { toast('신청 실패: ' + (e as Error).message, 'warn') }
  }, [toast])
  const cancelRoleUpgrade = useCallback(async () => {
    try {
      setAccount(await authApi.cancelRoleRequest())
      toast('권한 신청을 취소했습니다.', 'info')
    } catch (e) { toast('취소 실패: ' + (e as Error).message, 'warn') }
  }, [toast])

  // 새로고침 시 Refresh 쿠키로 세션을 복구한다. 세션이 없으면 로그인 화면을 유지한다.
  useEffect(() => {
    let cancelled = false
    const wasDemo = localStorage.getItem(DEMO_FLAG) === 'true'
    authApi.refresh()
      .then((res) => { if (!cancelled) applySession(res, wasDemo) })
      .catch(() => { /* 유효한 세션 없음 */ })
      .finally(() => { if (!cancelled) setAuthReady(true) })
    return () => { cancelled = true }
  }, [applySession])

  const goAuth = useCallback((v: AuthView) => {
    setAuthView(v)
    window.scrollTo({ top: 0 })
  }, [])

  const go = useCallback((v: ViewId, id?: number) => {
    setView(v)
    if (v === 'detail' && typeof id === 'number') setDetailId(id)
    setSidebarOpen(false)
    window.scrollTo({ top: 0 })
  }, [])

  const changeRole = useCallback((r: Role) => {
    if (!demoMode) {
      // 실제 로그인 계정의 권한은 auth-service 가 기준이며 운영 관리자만 변경할 수 있다.
      toast('권한은 운영 관리자만 변경할 수 있습니다.', 'warn')
      return
    }
    // 시연용 전환도 해당 역할의 데모 계정으로 토큰을 다시 받는다.
    // 화면만 바꾸면 서버가 이전 역할로 판단해 보호 API 가 막힌다.
    setDemoPending(true)
    authApi.demoLogin(r)
      .then((res) => {
        applySession(res, true)
        setDetailId(null)
        setView('home')
        toast(`[시연용] ${res.account.name} 님 화면으로 전환되었습니다.`, 'info')
      })
      .catch((e) => toast('권한 전환에 실패했습니다: ' + (e as Error).message, 'warn'))
      .finally(() => setDemoPending(false))
  }, [demoMode, applySession, toast])

  const toggleSide = useCallback(() => setSideCollapsed((v) => !v), [])

  const setFilters = useCallback((patch: Partial<Filters>) => {
    setFiltersState((prev) => ({ ...prev, ...patch }))
  }, [])
  const resetFilters = useCallback(() => setFiltersState(DEFAULT_FILTERS), [])

  // Access Token 만료 전에 Refresh 쿠키로 조용히 재발급받는다.
  useEffect(() => {
    if (!account || accessExpiresIn <= 0) return
    const delayMs = Math.max(10, accessExpiresIn - 60) * 1000
    const timer = window.setTimeout(() => {
      authApi.refresh()
        .then((res) => {
          setAccessToken(res.accessToken)
          setAccount(res.account)
          setAccessExpiresIn(res.expiresIn)
        })
        .catch(() => {
          clearSession()
          toast('세션이 만료되었습니다. 다시 로그인해 주세요.', 'warn')
        })
    }, delayMs)
    return () => window.clearTimeout(timer)
  }, [account, accessExpiresIn, clearSession, toast])

  // 계정과 권한의 기준은 auth-service 다. 운영 관리자로 로그인하면 계정 목록을 여기서 가져온다.
  const refreshAuthAccounts = useCallback(async () => {
    const list = await authApi.accounts()
    setAuthAccounts(list)
    setUsers(list.map((a) => ({ name: a.name, dept: a.dept, role: a.role })))
  }, [])

  useEffect(() => {
    if (account?.role !== 'admin') return
    refreshAuthAccounts().catch(() => { /* 목록을 못 가져와도 화면은 유지한다 */ })
  }, [account, refreshAuthAccounts])

  // 데모 진입 중에는 권한 전환으로 계정이 바뀌므로 시연용 안내를 유지한다.

  // ---- API 모드 연동 (VITE_USE_API=true) ----
  const refreshPrograms = useCallback(async () => {
    // 공개 카탈로그(기본 서비스 포함)는 로그인한 모든 사용자가 볼 수 있어야 하므로 /api/programs 로 로드.
    try { setPrograms(await api.list()) }
    catch { toast('프로그램을 불러오지 못했습니다.', 'warn'); return }
    // 운영 관리자만 대기·비공개까지 포함한 전체로 교체(검토용). role 이 deps 라 권한 전환 시 재로드된다.
    if (role === 'admin') {
      try { setPrograms(await api.listAll()) } catch { /* noop */ }
    }
  }, [role, toast])
  const refreshLogs = useCallback(async () => {
    try { setAdminLog(await api.reviewLogs()) } catch { /* noop */ }
  }, [])
  const refreshNotisFor = useCallback(async (name: string) => {
    try { setNotis(await api.notifications(name)) } catch { /* noop */ }
  }, [])
  const mergeProgram = useCallback((p: Program) => {
    setPrograms((prev) => prev.some((x) => x.id === p.id) ? prev.map((x) => x.id === p.id ? p : x) : [p, ...prev])
  }, [])
  const loadDetail = useCallback((id: number) => {
    if (!USE_API) return
    api.detail(id).then(mergeProgram).catch(() => { /* noop */ })
  }, [mergeProgram])

  // 백엔드가 인증을 요구하므로 프로그램/로그/계정 로드는 반드시 "로그인 이후"에 (토큰 보유 상태로) 수행한다.
  // (마운트 시점엔 아직 미로그인이라 401 → 예전엔 인증이 없어 동작하던 것이 auth 병합 후 비어 보였다)
  useEffect(() => {
    if (!USE_API || !loggedIn) return
    refreshPrograms()
    refreshLogs()
    api.users().then(setUsers).catch(() => { /* 비관리자는 403 → 무시 */ })
  }, [loggedIn, refreshPrograms, refreshLogs])

  useEffect(() => {
    if (!USE_API || !loggedIn) return
    refreshNotisFor(me.name)
  }, [loggedIn, me.name, refreshNotisFor])

  const progOf = useCallback(
    (id: number) => programs.find((p) => p.id === id) ?? null,
    [programs],
  )

  const canSee = useCallback((p: Program): boolean => {
    if (p.status !== 'public') return false
    if (p.scope === 'all') return true
    return p.dept === me.dept
  }, [me.dept])

  const publicPrograms = useMemo(() => programs.filter(canSee), [programs, canSee])
  const myPrograms = useMemo(
    () => programs.filter((p) => p.owner === me.name || p.mine),
    [programs, me.name],
  )
  const pendingPrograms = useMemo(
    () => programs.filter((p) => p.status === 'pending'),
    [programs],
  )

  const favorites = favByRole[role]
  const isFav = useCallback((id: number) => favorites.includes(id), [favorites])
  const toggleFav = useCallback((id: number) => {
    setFavByRole((prev) => {
      const cur = prev[role]
      const next = cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]
      return { ...prev, [role]: next }
    })
    toast(favorites.includes(id) ? '즐겨찾기에서 제거했습니다.' : '즐겨찾기에 추가했습니다.', 'info')
  }, [role, favorites, toast])
  const favPrograms = useMemo(
    () => favorites.map((id) => programs.find((p) => p.id === id)).filter(Boolean) as Program[],
    [favorites, programs],
  )

  const myNotis = useMemo(() => notis.filter((n) => n.to === me.name), [notis, me.name])
  const unreadCount = useMemo(() => myNotis.filter((n) => !n.read).length, [myNotis])
  const readNoti = useCallback((id: number) => {
    setNotis((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)))
    if (USE_API) api.readNoti(id).catch(() => { /* noop */ })
  }, [])
  const readAllNotis = useCallback(() => {
    setNotis((prev) => prev.map((n) => (n.to === me.name ? { ...n, read: true } : n)))
    if (USE_API) api.readAllNotis(me.name).catch(() => { /* noop */ })
    toast('모든 알림을 읽음 처리했습니다.', 'info')
  }, [me.name, toast])

  const pushNoti = useCallback((to: string, kind: NotiKind, title: string, sub: string, pid: number) => {
    setSeq((s) => {
      const id = s + 1
      setNotis((prev) => [{ id, to, kind, title, sub, read: false, pid }, ...prev])
      return id
    })
  }, [])

  const reviewProgram = useCallback((id: number, act: AdminAction, memo: string) => {
    const actLabel = { approve: '승인', reject: '반려', stop: '공개 중지', resume: '재공개' }[act]
    if (USE_API) {
      api.review(id, act, memo, me.name)
        .then(() => {
          refreshPrograms(); refreshLogs(); refreshNotisFor(me.name)
          toast(`${actLabel} 처리했습니다.`, act === 'reject' ? 'warn' : 'ok')
        })
        .catch((e) => toast('처리 실패: ' + (e as Error).message, 'warn'))
      return
    }
    setPrograms((prev) => prev.map((p) => {
      if (p.id !== id) return p
      const status: ProgramStatus =
        act === 'approve' ? 'public'
          : act === 'reject' ? 'rejected'
            : act === 'stop' ? 'stopped' : 'public'
      return { ...p, status, ...(act === 'reject' ? { rejectReason: memo } : {}), ...(act === 'stop' ? { stopReason: memo } : {}) }
    }))
    const target = programs.find((p) => p.id === id)
    if (target) {
      setAdminLog((prev) => [{ at: nowStamp(), pid: id, title: target.name, by: me.name, act, memo }, ...prev])
      const kind: NotiKind = act === 'approve' ? 'approve' : act === 'reject' ? 'reject' : 'submit'
      const label = act === 'approve' ? '승인되어 공개되었습니다.' : act === 'reject' ? '반려되었습니다.' : act === 'stop' ? '공개가 중지되었습니다.' : '다시 공개되었습니다.'
      pushNoti(target.owner, kind, `「${target.name}」 등록 요청이 ${label}`, `운영 관리자 ${me.name} · ${dot(today())}`, id)
    }
    toast(`${actLabel} 처리했습니다.`, act === 'reject' ? 'warn' : 'ok')
  }, [programs, me.name, pushNoti, toast, refreshPrograms, refreshLogs, refreshNotisFor])

  const setUserRole = useCallback((name: string, r: Role) => {
    // 로그인 상태(데모 포함)에서는 auth-service 가 권한의 기준이다.
    if (account) {
      const target = authAccounts.find((a) => a.name === name)
      if (!target) {
        toast('auth-service 에서 해당 계정을 찾을 수 없습니다.', 'warn')
        return
      }
      authApi.setRole(target.username, r)
        .then(() => refreshAuthAccounts())
        .then(() => toast(`${name} 님의 권한을 변경했습니다.`, 'ok'))
        .catch((e) => toast('권한 변경 실패: ' + (e as Error).message, 'warn'))
      return
    }

    setUsers((prev) => prev.map((u) => (u.name === name ? { ...u, role: r } : u)))
    if (USE_API) {
      api.setRole(name, r).then(() => api.users().then(setUsers)).catch((e) => toast('권한 변경 실패: ' + (e as Error).message, 'warn'))
    }
    toast(`${name} 님의 권한을 변경했습니다. (시연용)`, 'ok')
  }, [account, authAccounts, refreshAuthAccounts, toast])

  const addProgram = useCallback((input: NewProgramInput): void => {
    if (USE_API) {
      api.create({
        name: input.name, summary: input.summary, desc: input.desc, cat: input.cat || 'doc',
        owner: me.name, dept: input.dept || me.dept, ver: input.ver, repo: input.repo,
        branch: input.branch, tags: input.tags, purposes: input.purposes, run: input.run,
        scope: input.scope, readme: input.readme,
      })
        .then(() => { refreshPrograms(); refreshNotisFor(me.name); toast('등록 요청이 접수되었습니다. 운영 관리자 검토 후 공개됩니다.', 'ok') })
        .catch((e) => toast('등록 실패: ' + (e as Error).message, 'warn'))
      return
    }
    const id = Math.max(0, ...programs.map((p) => p.id)) + 1
    const t = today()
    const p: Program = {
      id, name: input.name, cat: (input.cat || 'doc') as Program['cat'],
      owner: me.name, dept: input.dept || me.dept,
      ver: input.ver || '1.0.0', updated: t, created: t, branch: input.branch || 'main',
      tags: input.tags, purposes: input.purposes, tech: input.tags,
      run: input.run.length ? input.run : ['gitea'],
      history: [{ ver: input.ver || '1.0.0', date: t, log: '최초 등록 요청' }],
      summary: input.summary, desc: input.desc, repo: input.repo,
      views: 0, likes: 0, downloads: 0, status: 'pending', scope: input.scope, mine: true,
      files: [], readme: input.readme ? input.readme.split('\n') : ['## 개요', input.summary],
      comments: [],
    }
    setPrograms((prev) => [p, ...prev])
    pushNoti(ROLE_USER.admin.name, 'submit', `「${p.name}」 등록 요청이 접수되었습니다.`, `${me.name} · ${me.dept} · ${dot(t)}`, id)
    toast('등록 요청이 접수되었습니다. 운영 관리자 검토 후 공개됩니다.', 'ok')
  }, [programs, me.name, me.dept, pushNoti, toast, refreshPrograms, refreshNotisFor])

  /** 등록자 본인 재배포 — 성공 시 true. 목록 갱신은 호출부가 상태 폴링 후 수행한다. */
  const redeployProgram = useCallback(async (id: number, version: string, note: string): Promise<boolean> => {
    if (!USE_API) {
      toast('재배포는 백엔드 연동 모드(VITE_USE_API=true)에서 동작합니다.', 'warn')
      return false
    }
    try {
      await api.redeployProgram(id, { version: version.trim() || undefined, note: note.trim() || undefined })
      refreshPrograms()
      toast('재배포를 시작했습니다. 빌드가 끝나면 새 버전이 자동 반영됩니다.', 'ok')
      return true
    } catch (e) {
      toast('재배포 요청 실패: ' + (e as Error).message, 'warn')
      return false
    }
  }, [toast, refreshPrograms])

  const addComment = useCallback((id: number, body: { user: string; dept: string; body: string }) => {
    if (USE_API) {
      api.addComment(id, body).then(() => loadDetail(id)).catch((e) => toast('의견 등록 실패: ' + (e as Error).message, 'warn'))
      toast('의견을 등록했습니다.', 'ok')
      return
    }
    const c: Comment = { user: body.user, dept: body.dept, time: today(), body: body.body, reply: null }
    setPrograms((prev) => prev.map((p) => (p.id === id ? { ...p, comments: [...p.comments, c] } : p)))
    toast('의견을 등록했습니다.', 'ok')
  }, [toast, loadDetail])

  const value: AppContextValue = {
    loggedIn, login, logout,
    authView, goAuth,
    account, demoMode, demoPending, authReady, signIn, requestRoleUpgrade, cancelRoleUpgrade,
    role, changeRole, me,
    view, detailId, go,
    sideCollapsed, toggleSide, sidebarOpen, setSidebarOpen,
    theme, toggleTheme, fontScale, setFontScale,
    myTab, setMyTab,
    programs, progOf, publicPrograms, myPrograms, canSee,
    filters, setFilters, resetFilters,
    favorites, isFav, toggleFav, favPrograms,
    notis, myNotis, unreadCount, readNoti, readAllNotis,
    adminLog, users, pendingPrograms, reviewProgram, setUserRole,
    addProgram, redeployProgram, addComment, loadDetail,
    toasts, toast, dismissToast,
    modal, openModal, closeModal,
  }

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useApp(): AppContextValue {
  const ctx = useContext(AppContext)
  if (!ctx) throw new Error('useApp must be used within AppProvider')
  return ctx
}
