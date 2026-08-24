import './My.css'
import { useState } from 'react'
import { num, dot, catOf } from '../../lib/helpers'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import { StatusBadge } from '../../components/program/Badges'
import { ProgramCard } from '../../components/program/ProgramCard'
import type { NotiKind, ProgramStatus } from '../../types'

type Tab = 'mine' | 'fav' | 'noti'
const STATUS_TABS: { id: 'all' | ProgramStatus; name: string }[] = [
  { id: 'all', name: '전체' }, { id: 'public', name: '공개 중' }, { id: 'pending', name: '승인 대기' },
  { id: 'rejected', name: '반려' }, { id: 'stopped', name: '공개 중지' },
]
const NOTI_ICON: Record<NotiKind, Parameters<typeof Icon>[0]['name']> = {
  comment: 'comment', reject: 'warn', submit: 'info', version: 'upload', approve: 'check',
}

export function My() {
  const { role, myPrograms, favPrograms, myNotis, unreadCount, readNoti, readAllNotis, go } = useApp()
  const canRegister = role === 'coder' || role === 'admin'
  const [tab, setTab] = useState<Tab>(canRegister ? 'mine' : 'fav')
  const [status, setStatus] = useState<'all' | ProgramStatus>('all')

  const isUser = role === 'user'
  const title = isUser ? '마이페이지' : '내 프로그램'

  const filteredMine = status === 'all' ? myPrograms : myPrograms.filter((p) => p.status === status)
  const count = (s: ProgramStatus) => myPrograms.filter((p) => p.status === s).length

  return (
    <div className="page container">
      <div className="page-head">
        <div className="page-title">{title}</div>
        <div className="page-desc">{isUser ? '즐겨찾기와 알림을 확인합니다.' : '등록 현황·승인 상태·즐겨찾기·알림을 확인합니다.'}</div>
      </div>

      {!isUser && (
        <div className="stat-row">
          <div className="stat-card"><div className="sc-l"><Icon name="list" size={14} />등록한 프로그램</div><div className="sc-n">{myPrograms.length}</div></div>
          <div className="stat-card"><div className="sc-l"><Icon name="check" size={14} />공개 중</div><div className="sc-n">{count('public')}</div></div>
          <div className="stat-card"><div className="sc-l"><Icon name="info" size={14} />승인 대기</div><div className="sc-n">{count('pending')}</div></div>
          <div className="stat-card"><div className="sc-l"><Icon name="download" size={14} />총 다운로드</div><div className="sc-n">{num(myPrograms.reduce((s, p) => s + p.downloads, 0))}</div></div>
        </div>
      )}

      <div className="subtabs">
        {canRegister && <button className={tab === 'mine' ? 'on' : ''} onClick={() => setTab('mine')}>등록한 프로그램</button>}
        <button className={tab === 'fav' ? 'on' : ''} onClick={() => setTab('fav')}>즐겨찾기 ({favPrograms.length})</button>
        <button className={tab === 'noti' ? 'on' : ''} onClick={() => setTab('noti')}>알림 {unreadCount > 0 && `(${unreadCount})`}</button>
      </div>

      {tab === 'mine' && canRegister && (
        <div>
          <div className="status-filter">
            {STATUS_TABS.map((s) => (
              <button key={s.id} className={`tag-btn${status === s.id ? ' on' : ''}`} onClick={() => setStatus(s.id)}>{s.name}</button>
            ))}
            <button className="btn btn-sm btn-primary" style={{ marginLeft: 'auto' }} onClick={() => go('register')}>
              <Icon name="plus" size={14} /> 새 프로그램 등록
            </button>
          </div>
          {filteredMine.length === 0 ? (
            <div className="empty"><div className="em-t">해당 상태의 프로그램이 없습니다.</div></div>
          ) : (
            <div className="panel">
              <div className="table-scroll">
                <table className="table">
                  <thead><tr><th>프로그램</th><th>업무 분야</th><th>버전</th><th>상태</th><th>조회/다운로드</th><th>최근</th><th></th></tr></thead>
                  <tbody>
                    {filteredMine.map((p) => (
                      <tr key={p.id}>
                        <td>
                          <div style={{ fontWeight: 600, cursor: 'pointer' }} onClick={() => go('detail', p.id)}>{p.name}</div>
                          {p.status === 'rejected' && p.rejectReason && (
                            <div className="reject-box">
                              <h5><Icon name="warn" size={13} /> 반려 사유</h5>
                              <p>{p.rejectReason}</p>
                            </div>
                          )}
                          {p.status === 'stopped' && p.stopReason && (
                            <div className="reject-box" style={{ borderColor: '#CFD6E0', background: '#F7F8FA' }}>
                              <h5 style={{ color: '#4A5768' }}><Icon name="info" size={13} /> 공개 중지 사유</h5>
                              <p>{p.stopReason}</p>
                            </div>
                          )}
                        </td>
                        <td>{catOf(p.cat).name}</td>
                        <td><span className="ver-chip">v{p.ver}</span></td>
                        <td><StatusBadge status={p.status} /></td>
                        <td>{num(p.views)} / {num(p.downloads)}</td>
                        <td>{dot(p.updated)}</td>
                        <td>
                          <div className="my-table-actions">
                            <button className="btn btn-sm" onClick={() => go('detail', p.id)}>보기</button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {tab === 'fav' && (
        favPrograms.length === 0 ? (
          <div className="empty">
            <div className="em-ico"><Icon name="star" size={26} /></div>
            <div className="em-t">즐겨찾기한 프로그램이 없습니다.</div>
            <button className="btn btn-sm" onClick={() => go('list')}>프로그램 탐색</button>
          </div>
        ) : (
          <div className="card-grid">{favPrograms.map((p) => <ProgramCard key={p.id} p={p} />)}</div>
        )
      )}

      {tab === 'noti' && (
        <div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
            <button className="btn btn-sm btn-ghost" onClick={readAllNotis}>모두 읽음</button>
          </div>
          {myNotis.length === 0 ? (
            <div className="empty"><div className="em-t">알림이 없습니다.</div></div>
          ) : myNotis.map((n) => (
            <div key={n.id} className={`noti-row ${n.read ? 'read' : 'unread'}`} onClick={() => { readNoti(n.id); go('detail', n.pid) }}>
              <span className="nr-dot" />
              <div>
                <div className="nr-t">{n.title}</div>
                <div className="nr-s">{n.sub}</div>
              </div>
              <span className="nr-k"><Icon name={NOTI_ICON[n.kind]} size={15} /></span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
