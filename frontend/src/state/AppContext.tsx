import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
  type ReactNode,
} from 'react'
import { ROLE_USER } from '../data/catalog'
import { USE_API, api } from '../api/client'
import { ADMIN_LOG_SEED, NOTIS_SEED, PROGRAMS, USERS_SEED } from '../data/programs'
import { nowStamp, today, dot } from '../lib/helpers'
import type {
  AdminAction, AdminLogEntry, AppUser, Comment, NotiKind, Notification, Program,
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

const FAVORITES_SEED: Record<Role, number[]> = {
  user: [4, 5], coder: [2, 5], admin: [1, 4],
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [loggedIn, setLoggedIn] = useState(() => localStorage.getItem('edu-auth') === '1')
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
  // 로그인 상태 유지: 브라우저 뒤로가기/새로고침 시 로그인 페이지가 아니라 메인으로 복귀
  useEffect(() => {
    localStorage.setItem('edu-auth', loggedIn ? '1' : '0')
  }, [loggedIn])

  const toggleTheme = useCallback(() => setTheme((t) => (t === 'light' ? 'dark' : 'light')), [])
  const setFontScale = useCallback((s: FontScale) => setFontScaleState(s), [])
  const [myTab, setMyTab] = useState<'mine' | 'fav' | 'noti'>('mine')

  const [programs, setPrograms] = useState<Program[]>(() => USE_API ? [] : PROGRAMS.map((p) => ({ ...p })))
  const [notis, setNotis] = useState<Notification[]>(() => USE_API ? [] : NOTIS_SEED.map((n) => ({ ...n })))
  const [adminLog, setAdminLog] = useState<AdminLogEntry[]>(() => ADMIN_LOG_SEED.map((a) => ({ ...a })))
  const [users, setUsers] = useState<AppUser[]>(() => USE_API ? [] : USERS_SEED.map((u) => ({ ...u })))
  const [favByRole, setFavByRole] = useState<Record<Role, number[]>>(FAVORITES_SEED)
  const [filters, setFiltersState] = useState<Filters>(DEFAULT_FILTERS)
  // 백엔드 연동 활성 여부. USE_API 여도 백엔드 연결 실패 시 false(데모 폴백)로 내려간다.
  const [apiActive, setApiActive] = useState(USE_API)

  const [toasts, setToasts] = useState<Toast[]>([])
  const [modal, setModal] = useState<ReactNode>(null)
  const [, setSeq] = useState(1000)

  const me = ROLE_USER[role]

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

  const login = useCallback(() => { setLoggedIn(true); setView('home') }, [])
  const logout = useCallback(() => { setLoggedIn(false); setView('home'); setDetailId(null) }, [])

  const go = useCallback((v: ViewId, id?: number) => {
    setView(v)
    if (v === 'detail' && typeof id === 'number') setDetailId(id)
    setSidebarOpen(false)
    window.scrollTo({ top: 0 })
  }, [])

  const changeRole = useCallback((r: Role) => {
    setRole(r)
    setDetailId(null)
    setView('home')
    toast(`[시연용] ${ROLE_USER[r].name} 님 화면으로 전환되었습니다.`, 'info')
  }, [toast])

  const toggleSide = useCallback(() => setSideCollapsed((v) => !v), [])

  const setFilters = useCallback((patch: Partial<Filters>) => {
    setFiltersState((prev) => ({ ...prev, ...patch }))
  }, [])
  const resetFilters = useCallback(() => setFiltersState(DEFAULT_FILTERS), [])

  // ---- API 모드 연동 (VITE_USE_API=true) ----
  const refreshPrograms = useCallback(async () => {
    try { setPrograms(await api.listAll()) } catch { toast('프로그램을 불러오지 못했습니다.', 'warn') }
  }, [toast])
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

  useEffect(() => {
    if (!USE_API) return
    let cancelled = false
    void (async () => {
      try {
        const list = await api.listAll()
        if (cancelled) return
        setPrograms(list)
        refreshLogs()
        try { setUsers(await api.users()) } catch { /* noop */ }
      } catch {
        if (cancelled) return
        // 백엔드에 연결하지 못하면 데모(목업) 데이터로 폴백
        setApiActive(false)
        setPrograms(PROGRAMS.map((p) => ({ ...p })))
        setUsers(USERS_SEED.map((u) => ({ ...u })))
        setNotis(NOTIS_SEED.map((n) => ({ ...n })))
        toast('백엔드에 연결하지 못해 데모 데이터로 표시합니다.', 'warn')
      }
    })()
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!USE_API || !apiActive) return
    refreshNotisFor(ROLE_USER[role].name)
  }, [role, apiActive, refreshNotisFor])

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
    if (apiActive) api.readNoti(id).catch(() => { /* noop */ })
  }, [])
  const readAllNotis = useCallback(() => {
    setNotis((prev) => prev.map((n) => (n.to === me.name ? { ...n, read: true } : n)))
    if (apiActive) api.readAllNotis(me.name).catch(() => { /* noop */ })
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
    if (apiActive) {
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
    setUsers((prev) => prev.map((u) => (u.name === name ? { ...u, role: r } : u)))
    if (apiActive) {
      api.setRole(name, r).then(() => api.users().then(setUsers)).catch((e) => toast('권한 변경 실패: ' + (e as Error).message, 'warn'))
    }
    toast(`${name} 님의 권한을 변경했습니다. (시연용)`, 'ok')
  }, [toast])

  const addProgram = useCallback((input: NewProgramInput): void => {
    if (apiActive) {
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

  const addComment = useCallback((id: number, body: { user: string; dept: string; body: string }) => {
    if (apiActive) {
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
    addProgram, addComment, loadDetail,
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
