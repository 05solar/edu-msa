import './Topbar.css'
import { useState } from 'react'
import { NAV, NAV_ALT, VIEW_TITLE } from '../../data/catalog'
import { initialOf } from '../../lib/helpers'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'

export function Topbar() {
  const { view, role, me, go, setSidebarOpen, setFilters } = useApp()
  const [q, setQ] = useState('')

  const nav = NAV.find((n) => n.view === view)
  const alt = nav ? NAV_ALT[nav.view]?.[role] : undefined
  const title = VIEW_TITLE[view]?.label ?? alt?.label ?? nav?.label ?? '홈'
  const sub = VIEW_TITLE[view]?.sub ?? alt?.sub ?? nav?.sub ?? '교육청 내부 업무 프로그램 공유 플랫폼'

  const submitSearch = () => {
    setFilters({ q })
    go('list')
  }

  return (
    <header className="topbar">
      <div className="container topbar-inner">
        <button className="mobile-toggle" onClick={() => setSidebarOpen(true)} title="메뉴 열기" aria-label="메뉴 열기">
          <Icon name="menu" size={16} />
        </button>
        <div className="top-title">{title}<span className="ts">{sub}</span></div>
        <div className="topbar-right">
          <div className="top-search">
            <span className="ico"><Icon name="search" size={14} /></span>
            <input
              type="text"
              placeholder="업무·프로그램명으로 검색"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') submitSearch() }}
            />
          </div>
          <div className="avatar">{initialOf(me.name)}</div>
        </div>
      </div>
    </header>
  )
}
