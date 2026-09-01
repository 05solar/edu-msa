import './My.css'
import { Fragment, useEffect, useState } from 'react'
import { num, dot, catOf } from '../../lib/helpers'
import { ROLE_LABEL } from '../../data/catalog'
import { useApp } from '../../state/AppContext'
import { api, USE_API } from '../../api/client'
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

/** '1.0.0' → '1.0.1' — 마지막 숫자 조각만 +1 (관례적 패치 버전 올림 기본값). */
function bumpPatch(ver: string): string {
  const m = ver.match(/^(.*?)(\d+)$/)
  return m ? m[1] + (parseInt(m[2], 10) + 1) : ver
}

const DEPLOYING = ['pending', 'validating', 'building', 'deploying']

export function My() {
  const { role, myPrograms, favPrograms, myNotis, unreadCount, readNoti, readAllNotis, go, myTab, setMyTab,
    account, demoMode, requestRoleUpgrade, cancelRoleUpgrade, redeployProgram } = useApp()
  const canRegister = role === 'coder' || role === 'admin'
  const tab: Tab = (!canRegister && myTab === 'mine') ? 'fav' : myTab
  const setTab = setMyTab
  const [status, setStatus] = useState<'all' | ProgramStatus>('all')
  const [reqRole, setReqRole] = useState<'coder' | 'admin' | ''>('')
  const [reqReason, setReqReason] = useState('')

  // 재배포 인라인 패널 + 진행 상태 폴링
  const [redeployFor, setRedeployFor] = useState<number | null>(null)
  const [newVer, setNewVer] = useState('')
  const [redeployNote, setRedeployNote] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [watch, setWatch] = useState<Record<number, string>>({})

  const openRedeploy = (id: number, ver: string) => {
    setRedeployFor(id); setNewVer(bumpPatch(ver)); setRedeployNote('')
  }
  const submitRedeploy = async (id: number) => {
    setSubmitting(true)
    const ok = await redeployProgram(id, newVer, redeployNote)
    setSubmitting(false)
    if (ok) {
      setRedeployFor(null)
      setWatch((w) => ({ ...w, [id]: 'pending' }))
    }
  }

  // 재배포를 건 프로그램의 배포 상태를 완료(running/failed)까지 3초마다 폴링한다.
  useEffect(() => {
    const ids = Object.entries(watch).filter(([, s]) => DEPLOYING.includes(s)).map(([id]) => Number(id))
    if (!USE_API || ids.length === 0) return
    const t = window.setInterval(() => {
      ids.forEach((id) => {
        api.deploymentOf(id)
          .then((d) => { if (d) setWatch((w) => ({ ...w, [id]: d.status })) })
          .catch(() => { /* 일시 오류 무시 */ })
      })
    }, 3000)
    return () => window.clearInterval(t)
  }, [watch])

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

      {account && !demoMode && account.role !== 'admin' && (
        <div className="panel" style={{ marginBottom: 16 }}>
          <div className="panel-head"><div className="panel-title">내 권한</div></div>
          <div className="panel-body">
            <div style={{ marginBottom: 10 }}>현재 권한: <b>{ROLE_LABEL[account.role]}</b></div>
            {account.requestedRole ? (
              <div>
                <div>신청 중: <b>{ROLE_LABEL[account.requestedRole]}</b> — 운영 관리자 승인 대기</div>
                {account.roleRequestReason && (
                  <div style={{ color: 'var(--ink-400)', fontSize: 13, marginTop: 4 }}>사유: {account.roleRequestReason}</div>
                )}
                <button className="btn btn-sm" style={{ marginTop: 10 }} onClick={() => cancelRoleUpgrade()}>신청 취소</button>
              </div>
            ) : (
              <div>
                <div className="field-row" style={{ maxWidth: 520 }}>
                  <select className="input" value={reqRole} onChange={(e) => setReqRole(e.target.value as 'coder' | 'admin' | '')}>
                    <option value="">신청할 권한 선택</option>
                    {account.role === 'user' && <option value="coder">바이브 코더 — 프로그램 등록·배포</option>}
                    <option value="admin">운영 관리자 — 검토·권한·배포 관리</option>
                  </select>
                </div>
                <textarea
                  className="input"
                  rows={2}
                  maxLength={300}
                  style={{ marginTop: 8, maxWidth: 520, width: '100%' }}
                  value={reqReason}
                  placeholder="신청 사유 (선택, 최대 300자)"
                  onChange={(e) => setReqReason(e.target.value)}
                />
                <div style={{ marginTop: 10 }}>
                  <button
                    className="btn btn-primary btn-sm"
                    disabled={!reqRole}
                    onClick={() => { if (reqRole) { requestRoleUpgrade(reqRole, reqReason.trim()); setReqRole(''); setReqReason('') } }}
                  >권한 신청</button>
                </div>
                <div style={{ color: 'var(--ink-400)', fontSize: 12, marginTop: 6 }}>상향 권한은 운영 관리자 승인 후 적용됩니다.</div>
              </div>
            )}
          </div>
        </div>
      )}

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
                      <Fragment key={p.id}>
                        <tr>
                          <td>
                            <div style={{ fontWeight: 600, cursor: 'pointer' }} onClick={() => go('detail', p.id)}>{p.name}</div>
                            {p.status === 'rejected' && p.rejectReason && (
                              <div className="reject-box">
                                <h5><Icon name="warn" size={13} /> 반려 사유</h5>
                                <p>{p.rejectReason}</p>
                              </div>
                            )}
                            {p.status === 'stopped' && p.stopReason && (
                              <div className="reject-box" style={{ borderColor: 'var(--line)', background: 'var(--surface-subtle)' }}>
                                <h5 style={{ color: '#4A5768' }}><Icon name="info" size={13} /> 공개 중지 사유</h5>
                                <p>{p.stopReason}</p>
                              </div>
                            )}
                          </td>
                          <td>{catOf(p.cat).name}</td>
                          <td><span className="ver-chip">v{p.ver}</span></td>
                          <td>
                            <StatusBadge status={p.status} />
                            {watch[p.id] && (
                              DEPLOYING.includes(watch[p.id]) ? (
                                <div style={{ fontSize: 12, color: 'var(--brand)', marginTop: 4 }}>배포 중… ({watch[p.id]})</div>
                              ) : watch[p.id] === 'running' ? (
                                <div style={{ fontSize: 12, color: 'var(--ok, #17693a)', marginTop: 4 }}>✓ 새 버전 배포 완료</div>
                              ) : (
                                <div style={{ fontSize: 12, color: 'var(--danger, #9c2121)', marginTop: 4 }}>배포 실패 — 상세에서 로그 확인</div>
                              )
                            )}
                          </td>
                          <td>{num(p.views)} / {num(p.downloads)}</td>
                          <td>{dot(p.updated)}</td>
                          <td>
                            <div className="my-table-actions">
                              {p.status === 'public' && (
                                <button
                                  className="btn btn-sm btn-primary"
                                  disabled={DEPLOYING.includes(watch[p.id] ?? '')}
                                  onClick={() => (redeployFor === p.id ? setRedeployFor(null) : openRedeploy(p.id, p.ver))}
                                  title="GitHub 레포를 갱신했다면 새 버전으로 다시 배포합니다"
                                >
                                  <Icon name="upload" size={13} /> 재배포
                                </button>
                              )}
                              <button className="btn btn-sm" onClick={() => go('detail', p.id)}>보기</button>
                            </div>
                          </td>
                        </tr>
                        {redeployFor === p.id && (
                          <tr>
                            <td colSpan={7} style={{ background: 'var(--surface-subtle)' }}>
                              <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 10, padding: '6px 2px' }}>
                                <b style={{ fontSize: 13.5 }}>새 버전으로 재배포</b>
                                <span style={{ fontSize: 12.5, color: 'var(--ink-400)' }}>
                                  레포(<code style={{ fontSize: 12 }}>{p.repo}</code> · {p.branch})의 최신 코드를 다시 빌드해 배포합니다.
                                </span>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', width: '100%' }}>
                                  <span style={{ fontSize: 13 }}>버전 v{p.ver} →</span>
                                  <input
                                    className="input" style={{ width: 110 }} value={newVer}
                                    onChange={(e) => setNewVer(e.target.value)} placeholder={bumpPatch(p.ver)}
                                  />
                                  <input
                                    className="input" style={{ flex: 1, minWidth: 200 }} value={redeployNote}
                                    onChange={(e) => setRedeployNote(e.target.value)}
                                    placeholder="변경 내용 (업데이트 내역에 기록, 예: 계산 오류 수정)"
                                    maxLength={120}
                                  />
                                  <button className="btn btn-sm btn-primary" disabled={submitting || !newVer.trim()} onClick={() => submitRedeploy(p.id)}>
                                    {submitting ? '요청 중…' : '재배포 실행'}
                                  </button>
                                  <button className="btn btn-sm" disabled={submitting} onClick={() => setRedeployFor(null)}>취소</button>
                                </div>
                              </div>
                            </td>
                          </tr>
                        )}
                      </Fragment>
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
