import type { IconName } from '../icons/Icon'

export type Role = 'user' | 'coder' | 'admin'
export type CategoryId = 'doc' | 'student' | 'curri' | 'budget' | 'facil' | 'data' | 'civil'
export type PurposeId = 'auto' | 'gen' | 'verify' | 'analyze' | 'summary' | 'search' | 'dash'
export type RunTypeId = 'web' | 'download' | 'installer' | 'gitea' | 'manual'
export type ProgramStatus = 'draft' | 'pending' | 'public' | 'rejected' | 'stopped'
export type Scope = 'all' | 'dept'
export type NotiKind = 'comment' | 'reject' | 'submit' | 'version' | 'approve'

export interface Category { id: CategoryId; name: string; icon: IconName; color: string }
export interface Purpose { id: PurposeId; name: string; icon: IconName }
export interface RunType { id: RunTypeId; name: string; icon: IconName; desc: string }

export interface HistoryEntry { ver: string; date: string; log: string }
export interface ProgramFile { name: string; size: string; type: string }
export interface CommentReply { user: string; dept: string; time: string; body: string }
export interface Comment {
  user: string; dept: string; time: string; body: string; reply: CommentReply | null
}

export interface Program {
  id: number
  name: string
  cat: CategoryId
  owner: string
  dept: string
  ver: string
  updated: string
  created: string
  branch?: string
  tags: string[]
  purposes: PurposeId[]
  tech: string[]
  run: RunTypeId[]
  history: HistoryEntry[]
  features?: string[]
  summary: string
  desc: string
  repo: string
  views: number
  likes: number
  downloads: number
  status: ProgramStatus
  scope: Scope
  mine?: boolean
  stopReason?: string
  rejectReason?: string
  files: ProgramFile[]
  readme: string[]
  comments: Comment[]
  pendingUpdate?: boolean
}

export interface Notification {
  id: number
  to: string
  kind: NotiKind
  title: string
  sub: string
  read: boolean
  pid: number
}

export type AdminAction = 'approve' | 'reject' | 'stop' | 'resume'
export interface AdminLogEntry {
  at: string
  pid: number
  title: string
  by: string
  act: AdminAction
  memo: string
}

export interface AppUser { name: string; dept: string; role: Role }

export interface AiSource { id: string; name: string; desc: string }
export interface AiModel { id: string; name: string; sub: string }

export interface NavItem {
  view: ViewId
  label: string
  icon: IconName
  group: 'main' | 'ops'
  roles: Role[]
  sub: string
}

export type ViewId =
  | 'home' | 'list' | 'ai' | 'register' | 'my' | 'admin' | 'detail'
