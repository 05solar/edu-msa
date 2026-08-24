import './Admin.css'
import { Fragment, useState } from 'react'
import { CATEGORIES, ROLE_LABEL } from '../../data/catalog'
import { catOf, num, dot } from '../../lib/helpers'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import { StatusBadge } from '../../components/program/Badges'
import { DeployResultModal } from '../../components/DeployResultModal/DeployResultModal'
import { USE_API, api } from '../../api/client'
import type { AdminAction, Role } from '../../types'

type Tab = 'pending' | 'all' | 'log' | 'stats' | 'category'
const TABS: { id: Tab; name: string }[] = [
  { id: 'pending', name: '검토 대기' }, { id: 'all', name: '전체 프로그램' },
  { id: 'log', name: '처리 이력' }, { id: 'stats', name: '운영 현황' }, { id: 'category', name: '카테고리 관리' },
]
const REASONS = ['개인정보 처리 검토 필요', '정보보안 검토 필요', '중복 프로그램으로 확인', '설명·사용법 보완 필요', '표준 서비스 규격 미준수']
const ACT_BADGE: Record<AdminAction, { cls: string; label: string }> = {
  approve: { cls: 'badge-ok', label: '승인' }, reject: { cls: 'badge-danger', label: '반려' },
  stop: { cls: 'badge-stop', label: '공개 중지' }, resume: { cls: 'badge-blue', label: '재공개' },
}

export function Admin() {
  const { pendingPrograms, programs, adminLog, users, reviewProgram, setUserRole, go, toast, openModal, me } = useApp()
  const [tab, setTab] = useState<Tab>('pending')
  const [rejectingId, setRejectingId] = useState<number | null>(null)
  const [reason, setReason] = useState('')
  const [deployingId, setDeployingId] = useState<number | null>(null)

  const deploy = (id: number, repo: string, branch?: string) => {
    if (!USE_API) { toast('배포는 백엔드 연동 모드(VITE_USE_API=true)에서 실행됩니다.', 'warn'); return }
    setDeployingId(id)
    api.deployProgram(id, repo, branch || 'main', me.name)
      .then((res) => openModal(<DeployResultModal res={res} />))
      .catch((e) => toast('배포 요청 실패: ' + (e as Error).message, 'warn'))
      .finally(() => setDeployingId(null))
  }

  const approve = (id: number) => { reviewProgram(id, 'approve', '검토 체크리스트 확인 · 공개 전환') }
  const doReject = (id: number) => {
    if (!reason.trim()) return toast('반려 사유를 입력해 주세요.', 'warn')
    reviewProgram(id, 'reject', reason.trim())
    setRejectingId(null); setReason('')
  }

  const total = programs.length
  const pub = programs.filter((p) => p.status === 'public').length
  const pending = programs.filter((p) => p.status === 'pending').length
  const rejected = programs.filter((p) => p.status === 'rejected').length
  const maxCat = Math.max(1, ...CATEGORIES.map((c) => programs.filter((p) => p.cat === c.id).length))

  return (
    <div className="page container">
      <div className="page-head">
        <div className="page-title">운영 관리자</div>
        <div className="page-desc">등록 검토·처리 이력·운영 현황·카테고리를 관리합니다.</div>
      </div>

      <div className="subtabs">
        {TABS.map((t) => (
          <button key={t.id} className={tab === t.id ? 'on' : ''} onClick={() => setTab(t.id)}>
            {t.name}{t.id === 'pending' && pendingPrograms.length > 0 && ` (${pendingPrograms.length})`}
          </button>
        ))}
      </div>

      {tab === 'pending' && (
        pendingPrograms.length === 0 ? (
          <div className="empty"><div className="em-t">검토 대기 중인 요청이 없습니다.</div></div>
        ) : (
          <div className="panel"><div className="table-scroll"><table className="table">
            <thead><tr><th>프로그램</th><th>등록자</th><th>업무 분야</th><th>요청일</th><th style={{ width: 200 }}>처리</th></tr></thead>
            <tbody>
              {pendingPrograms.map((p) => (
                <Fragment key={p.id}>
                  <tr>
                    <td><div style={{ fontWeight: 600, cursor: 'pointer' }} onClick={() => go('detail', p.id)}>{p.name}</div>
                      <div style={{ fontSize: 12, color: 'var(--ink-400)' }}>{p.summary}</div></td>
                    <td>{p.owner} · {p.dept}</td>
                    <td>{catOf(p.cat).name}</td>
                    <td>{dot(p.created)}</td>
                    <td>
                      <div className="my-table-actions">
                        <button className="btn btn-sm btn-ok" onClick={() => approve(p.id)}>승인</button>
                        <button className="btn btn-sm btn-navy" disabled={deployingId === p.id} onClick={() => deploy(p.id, p.repo, p.branch)}>
                          {deployingId === p.id ? '배포 중…' : '배포'}
                        </button>
                        <button className="btn btn-sm btn-danger" onClick={() => { setRejectingId(rejectingId === p.id ? null : p.id); setReason('') }}>반려</button>
                      </div>
                    </td>
                  </tr>
                  {rejectingId === p.id && (
                    <tr className="reject-inline">
                      <td colSpan={5}>
                        <div className="reject-editor">
                          <h5>반려 사유</h5>
                          <div className="reason-chips">
                            {REASONS.map((r) => <button key={r} onClick={() => setReason(r)}>{r}</button>)}
                          </div>
                          <textarea className="textarea" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="반려 사유를 입력하면 등록자에게 알림으로 전달됩니다." />
                          <div className="reject-editor-actions">
                            <button className="btn btn-sm" onClick={() => setRejectingId(null)}>취소</button>
                            <button className="btn btn-sm btn-danger" onClick={() => doReject(p.id)}>반려 처리</button>
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table></div></div>
        )
      )}

      {tab === 'all' && (
        <div className="panel"><div className="table-scroll"><table className="table">
          <thead><tr><th>프로그램</th><th>등록자</th><th>분야</th><th>상태</th><th>조회/다운</th><th style={{ width: 160 }}>처리</th></tr></thead>
          <tbody>
            {programs.map((p) => (
              <tr key={p.id}>
                <td><span style={{ fontWeight: 600, cursor: 'pointer' }} onClick={() => go('detail', p.id)}>{p.name}</span></td>
                <td>{p.owner}</td>
                <td>{catOf(p.cat).name}</td>
                <td><StatusBadge status={p.status} /></td>
                <td>{num(p.views)} / {num(p.downloads)}</td>
                <td>
                  <div className="my-table-actions">
                    {p.status === 'public' && <button className="btn btn-sm" onClick={() => reviewProgram(p.id, 'stop', '운영 판단에 따른 공개 중지')}>공개 중지</button>}
                    {p.status === 'stopped' && <button className="btn btn-sm btn-ok" onClick={() => reviewProgram(p.id, 'resume', '재공개')}>재공개</button>}
                    {p.status === 'pending' && <button className="btn btn-sm btn-ok" onClick={() => approve(p.id)}>승인</button>}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table></div></div>
      )}

      {tab === 'log' && (
        <div className="panel"><div className="panel-body">
          {adminLog.map((l, i) => {
            const b = ACT_BADGE[l.act]
            return (
              <div key={i} className="log-row">
                <div className="log-act"><span className={`badge ${b.cls}`}><span className="dot" />{b.label}</span><div className="log-when">{l.at}</div></div>
                <div className="log-body">
                  <div className="log-title">{l.title}</div>
                  <div className="log-memo">{l.memo}</div>
                  <div className="log-by">처리자: {l.by}</div>
                </div>
              </div>
            )
          })}
        </div></div>
      )}

      {tab === 'stats' && (
        <div>
          <div className="stat-row">
            <div className="stat-card"><div className="sc-l">전체 프로그램</div><div className="sc-n">{total}</div></div>
            <div className="stat-card"><div className="sc-l">공개 중</div><div className="sc-n">{pub}</div></div>
            <div className="stat-card"><div className="sc-l">검토 대기</div><div className="sc-n">{pending}</div></div>
            <div className="stat-card"><div className="sc-l">반려</div><div className="sc-n">{rejected}</div></div>
          </div>
          <div className="grid-2" style={{ gap: 20 }}>
            <div className="panel">
              <div className="panel-head"><div className="panel-title">업무 분야별 등록 현황</div></div>
              <div className="panel-body">
                {CATEGORIES.map((c) => {
                  const n = programs.filter((p) => p.cat === c.id).length
                  return (
                    <div key={c.id} className="bar-row">
                      <div className="br-h"><span>{c.name}</span><b>{n}</b></div>
                      <div className="bar-track"><i style={{ width: `${(n / maxCat) * 100}%`, background: c.color }} /></div>
                    </div>
                  )
                })}
              </div>
            </div>
            <div className="panel">
              <div className="panel-head"><div className="panel-title">사용자 권한 관리</div></div>
              <div className="panel-body">
                <div className="table-scroll"><table className="table">
                  <thead><tr><th>이름</th><th>부서</th><th>권한</th></tr></thead>
                  <tbody>
                    {users.map((u) => (
                      <tr key={u.name}>
                        <td>{u.name}</td>
                        <td>{u.dept}</td>
                        <td>
                          <select className="role-mini" value={u.role} onChange={(e) => setUserRole(u.name, e.target.value as Role)}>
                            {(['user', 'coder', 'admin'] as Role[]).map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
                          </select>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table></div>
              </div>
            </div>
          </div>
        </div>
      )}

      {tab === 'category' && (
        <div className="panel"><div className="panel-body">
          {CATEGORIES.map((c) => (
            <div key={c.id} className="cat-manage-row">
              <span className="cm-ico" style={{ color: c.color }}><Icon name={c.icon} size={17} /></span>
              <div>
                <div className="cm-name">{c.name}</div>
                <div className="cm-cnt">{programs.filter((p) => p.cat === c.id).length}개 프로그램</div>
              </div>
              <div className="cm-act">
                <button className="btn btn-sm" onClick={() => toast('[시연용] 카테고리 편집은 준비 중입니다.', 'info')}>편집</button>
              </div>
            </div>
          ))}
          <button className="btn btn-primary btn-sm" style={{ marginTop: 8 }} onClick={() => toast('[시연용] 카테고리 추가는 준비 중입니다.', 'info')}>
            <Icon name="plus" size={14} /> 카테고리 추가
          </button>
        </div></div>
      )}
    </div>
  )
}
