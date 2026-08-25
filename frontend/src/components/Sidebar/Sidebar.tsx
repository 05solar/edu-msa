import './Sidebar.css'
import { NAV, NAV_ALT, ROLE_LABEL } from '../../data/catalog'
import { initialOf } from '../../lib/helpers'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import type { NavItem, Role } from '../../types'

export function Sidebar() {
  const {
    role, changeRole, demoMode, demoPending, view, go, me, sideCollapsed, toggleSide,
    sidebarOpen, setSidebarOpen, unreadCount, pendingPrograms,
  } = useApp()

  const items = NAV.filter((n) => n.roles.includes(role))
  const mainItems = items.filter((n) => n.group === 'main')
  const opsItems = items.filter((n) => n.group === 'ops')

  const labelOf = (n: NavItem) => NAV_ALT[n.view]?.[role]?.label ?? n.label
  const badgeOf = (n: NavItem): number => {
    if (n.view === 'my') return unreadCount
    if (n.view === 'admin') return pendingPrograms.length
    return 0
  }

  const renderBtn = (n: NavItem) => {
    const badge = badgeOf(n)
    return (
      <button
        key={n.view}
        className={view === n.view ? 'on' : ''}
        data-label={labelOf(n)}
        onClick={() => go(n.view)}
      >
        <span className="ico"><Icon name={n.icon} size={16} /></span>
        <span className="lbl">{labelOf(n)}</span>
        {badge > 0 && <span className="n-badge">{badge}</span>}
      </button>
    )
  }

  return (
    <>
      <aside className={`sidebar${sidebarOpen ? ' open' : ''}`} id="sidebar">
        <div className="side-brand" onClick={() => go('home')} title="홈으로">
          <div className="brand-mark">EC</div>
          <div>
            <span className="bn">교육청 코드 공유</span>
            <span className="bs">내부 업무 프로그램 공유 포털</span>
          </div>
        </div>

        <button className="side-toggle" onClick={toggleSide}
          title={sideCollapsed ? '메뉴 펼치기' : '메뉴 접기'}
          aria-label={sideCollapsed ? '메뉴 펼치기' : '메뉴 접기'}>
          <span className="chev">
            <Icon name={sideCollapsed ? 'chevrons-right' : 'chevrons-left'} size={17} />
          </span>
        </button>

        <nav className="side-nav">
          {mainItems.map(renderBtn)}
          {opsItems.length > 0 && (
            <>
              <div className="sep">운영</div>
              {opsItems.map(renderBtn)}
            </>
          )}
        </nav>

        <div className="side-demo">
          {/* 권한 전환은 데모 진입 전용. 실제 로그인 계정의 권한은 auth-service 가 기준이다. */}
          {demoMode && (
            <>
              <div className="sd-lbl">시연용 권한 전환</div>
              <select
                className="role-select"
                value={role}
                disabled={demoPending}
                onChange={(e) => changeRole(e.target.value as Role)}
                title="시연용 권한 전환"
              >
                <option value="user">외부 사용자</option>
                <option value="coder">내부 직원</option>
                <option value="admin">운영 관리자</option>
              </select>
            </>
          )}
          <div className="side-who">
            <div className="avatar">{initialOf(me.name)}</div>
            <div>
              <div className="sw-n">{me.name}</div>
              <div className="sw-d">{me.dept} · {ROLE_LABEL[role]}</div>
            </div>
          </div>
          <div className="side-note">
            {demoMode ? (
              <>시연용 데모 계정으로 전환합니다.<br />실제 로그인 시에는 계정 권한으로 결정됩니다.</>
            ) : (
              <>auth-service 계정으로 로그인한 상태입니다.<br />권한 변경은 운영 관리자가 수행합니다.</>
            )}
          </div>
        </div>
      </aside>
      <div
        className={`side-backdrop${sidebarOpen ? ' on' : ''}`}
        onClick={() => setSidebarOpen(false)}
      />
    </>
  )
}
