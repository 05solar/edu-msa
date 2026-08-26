import type { CSSProperties } from 'react'

/*
 * 인라인 SVG 아이콘 세트 — 이모지 대체 (DESIGN.md 0절: 이모지 금지)
 * 모든 아이콘은 24x24 viewBox, stroke 기반, currentColor 상속.
 * 데이터(분류/기능/제공방식)는 이 키 문자열로 아이콘을 참조한다.
 */
export type IconName =
  // 내비게이션
  | 'home' | 'list' | 'ai' | 'register' | 'my' | 'admin'
  // 업무 분야 (category)
  | 'doc' | 'student' | 'curri' | 'budget' | 'facil' | 'data' | 'civil'
  // 기능 유형 (purpose)
  | 'auto' | 'gen' | 'verify' | 'analyze' | 'summary' | 'search' | 'dash'
  // 제공 방식 (run type)
  | 'web' | 'download' | 'installer' | 'gitea' | 'manual'
  // UI
  | 'close' | 'check' | 'chevron-left' | 'chevron-right' | 'chevron-down'
  | 'chevrons-left' | 'chevrons-right' | 'sun' | 'moon' | 'text-size'
  | 'bell' | 'copy' | 'star' | 'star-filled' | 'plus' | 'external'
  | 'file' | 'arrow-right' | 'menu' | 'shield' | 'grid' | 'warn' | 'info'
  | 'git-branch' | 'eye' | 'comment' | 'calendar' | 'folder' | 'upload'
  | 'sparkle' | 'send' | 'logout'

const P: Record<IconName, JSX.Element> = {
  home: <><path d="M4 11l8-6 8 6" /><path d="M6 10v9h12v-9" /></>,
  logout: <><path d="M15 4h3a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-3" /><path d="M10 16l-4-4 4-4" /><path d="M6 12h9" /></>,
  list: <><rect x="4" y="4" width="7" height="7" rx="1" /><rect x="13" y="4" width="7" height="7" rx="1" /><rect x="4" y="13" width="7" height="7" rx="1" /><rect x="13" y="13" width="7" height="7" rx="1" /></>,
  ai: <><circle cx="12" cy="12" r="8" /><circle cx="12" cy="12" r="3" /></>,
  register: <><path d="M12 5v14" /><path d="M5 12h14" /></>,
  my: <><path d="M12 4l2.4 4.9 5.4.8-3.9 3.8.9 5.3L12 16.9 7.2 19l.9-5.3L4.2 9.7l5.4-.8z" /></>,
  admin: <><path d="M12 3l7 3v6c0 4.2-3 7.4-7 9-4-1.6-7-4.8-7-9V6z" /></>,

  doc: <><path d="M7 3h7l4 4v14H7z" /><path d="M14 3v4h4" /><path d="M10 12h6M10 16h6" /></>,
  student: <><path d="M3 9l9-4 9 4-9 4z" /><path d="M7 11v4c0 1.1 2.2 2 5 2s5-.9 5-2v-4" /></>,
  curri: <><path d="M5 4h9a2 2 0 0 1 2 2v14H7a2 2 0 0 1-2-2z" /><path d="M16 6h3v14H9" /></>,
  budget: <><circle cx="12" cy="12" r="8" /><path d="M9 9l3 6 3-6M9 12h6" /></>,
  facil: <><rect x="5" y="4" width="14" height="16" rx="1" /><path d="M9 8h2M13 8h2M9 12h2M13 12h2M10 20v-4h4v4" /></>,
  data: <><path d="M5 20V10M12 20V4M19 20v-7" /></>,
  civil: <><path d="M5 5h14v10H9l-4 4z" /><path d="M9 9h6M9 11h4" /></>,

  auto: <><circle cx="12" cy="12" r="3" /><path d="M12 4v2M12 18v2M4 12h2M18 12h2M6.3 6.3l1.4 1.4M16.3 16.3l1.4 1.4M17.7 6.3l-1.4 1.4M7.7 16.3l-1.4 1.4" /></>,
  gen: <><path d="M15 5l4 4L8 20H4v-4z" /><path d="M13 7l4 4" /></>,
  verify: <><circle cx="12" cy="12" r="8" /><path d="M8.5 12l2.5 2.5 4.5-5" /></>,
  analyze: <><path d="M4 19h16" /><path d="M6 16l4-5 3 3 5-7" /></>,
  summary: <><path d="M5 6h14M5 10h14M5 14h10M5 18h10" /></>,
  search: <><circle cx="11" cy="11" r="6" /><path d="M20 20l-4-4" /></>,
  dash: <><rect x="4" y="4" width="7" height="9" rx="1" /><rect x="13" y="4" width="7" height="5" rx="1" /><rect x="13" y="11" width="7" height="9" rx="1" /><rect x="4" y="15" width="7" height="5" rx="1" /></>,

  web: <><circle cx="12" cy="12" r="8" /><path d="M10 8.5l5 3.5-5 3.5z" /></>,
  download: <><path d="M12 4v10M8 11l4 4 4-4" /><path d="M5 19h14" /></>,
  installer: <><path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z" /><path d="M4 7.5l8 4.5 8-4.5M12 12v9" /></>,
  gitea: <><path d="M9 7H7a4 4 0 0 0 0 8h2M15 7h2a4 4 0 0 1 0 8h-2M8 11h8" /></>,
  manual: <><path d="M4 5a2 2 0 0 1 2-2h5v18H6a2 2 0 0 1-2-2z" /><path d="M20 5a2 2 0 0 0-2-2h-5v18h5a2 2 0 0 0 2-2z" /></>,

  close: <><path d="M6 6l12 12M18 6L6 18" /></>,
  check: <><path d="M5 12l4.5 4.5L19 7" /></>,
  'chevron-left': <><path d="M14 6l-6 6 6 6" /></>,
  'chevron-right': <><path d="M10 6l6 6-6 6" /></>,
  'chevron-down': <><path d="M6 10l6 6 6-6" /></>,
  'chevrons-left': <><path d="M11 6l-6 6 6 6" /><path d="M18 6l-6 6 6 6" /></>,
  'chevrons-right': <><path d="M6 6l6 6-6 6" /><path d="M13 6l6 6-6 6" /></>,
  sun: <><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M19 5l-1.5 1.5M6.5 17.5L5 19" /></>,
  moon: <><path d="M20 14.5A8 8 0 0 1 9.5 4 7 7 0 1 0 20 14.5z" /></>,
  'text-size': <><path d="M4 7V5h10v2M9 5v14M7 19h4M14 12v-1h6v1M17 11v8M15.5 19h3" /></>,
  bell: <><path d="M6 9a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6" /><path d="M10 19a2 2 0 0 0 4 0" /></>,
  copy: <><rect x="9" y="9" width="11" height="11" rx="2" /><path d="M5 15V5a2 2 0 0 1 2-2h8" /></>,
  star: <><path d="M12 4l2.4 4.9 5.4.8-3.9 3.8.9 5.3L12 16.9 7.2 19l.9-5.3L4.2 9.7l5.4-.8z" /></>,
  'star-filled': <><path d="M12 4l2.4 4.9 5.4.8-3.9 3.8.9 5.3L12 16.9 7.2 19l.9-5.3L4.2 9.7l5.4-.8z" fill="currentColor" stroke="none" /></>,
  plus: <><path d="M12 5v14M5 12h14" /></>,
  external: <><path d="M14 5h5v5M19 5l-8 8" /><path d="M17 13v5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1h5" /></>,
  file: <><path d="M7 3h7l4 4v14H7z" /><path d="M14 3v4h4" /></>,
  'arrow-right': <><path d="M5 12h14M13 6l6 6-6 6" /></>,
  menu: <><path d="M4 7h16M4 12h16M4 17h16" /></>,
  shield: <><path d="M12 3l7 3v6c0 4.2-3 7.4-7 9-4-1.6-7-4.8-7-9V6z" /></>,
  grid: <><rect x="4" y="4" width="7" height="7" rx="1" /><rect x="13" y="4" width="7" height="7" rx="1" /><rect x="4" y="13" width="7" height="7" rx="1" /><rect x="13" y="13" width="7" height="7" rx="1" /></>,
  warn: <><path d="M12 4l9 16H3z" /><path d="M12 10v4M12 17h.01" /></>,
  info: <><circle cx="12" cy="12" r="8" /><path d="M12 11v5M12 8h.01" /></>,
  'git-branch': <><circle cx="7" cy="6" r="2" /><circle cx="7" cy="18" r="2" /><circle cx="17" cy="8" r="2" /><path d="M7 8v8M17 10c0 4-4 3-10 6" /></>,
  eye: <><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6-10-6-10-6z" /><circle cx="12" cy="12" r="2.5" /></>,
  comment: <><path d="M5 5h14v10H9l-4 4z" /></>,
  calendar: <><rect x="4" y="5" width="16" height="16" rx="2" /><path d="M4 9h16M8 3v4M16 3v4" /></>,
  folder: <><path d="M4 7a1 1 0 0 1 1-1h4l2 2h8a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z" /></>,
  upload: <><path d="M12 20V10M8 13l4-4 4 4" /><path d="M5 5h14" /></>,
  sparkle: <><path d="M12 4l1.6 4.4L18 10l-4.4 1.6L12 16l-1.6-4.4L6 10l4.4-1.6z" /></>,
  send: <><path d="M4 12l16-7-7 16-2.5-6.5z" /></>,
}

interface IconProps {
  name: IconName
  size?: number
  className?: string
  style?: CSSProperties
  strokeWidth?: number
}

export function Icon({ name, size = 16, className, style, strokeWidth = 1.8 }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      style={{ flexShrink: 0, display: 'block', ...style }}
      aria-hidden="true"
      focusable="false"
    >
      {P[name]}
    </svg>
  )
}
