import './Detail.css'
import { useEffect, useState, type ReactNode } from 'react'
import {
  catOf, num, dot, cloneCmd, repoName, branchOf, featuresOf, runOf, runTypeOf, initialOf, statusLabel,
} from '../../lib/helpers'
import { SCOPE_SHORT } from '../../data/catalog'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import { PurposeBadges, TechLine, StatusBadge } from '../../components/program/Badges'
import { RunModal } from '../../components/RunModal/RunModal'
import { USE_API, api, type DeploymentResponse } from '../../api/client'

type Tab = 'desc' | 'readme' | 'history' | 'comments'

function renderReadme(lines: string[]): ReactNode[] {
  const out: ReactNode[] = []
  let bullets: string[] = []
  const flush = (key: string) => {
    if (bullets.length) {
      out.push(<ul key={key}>{bullets.map((b, i) => <li key={i}>{b}</li>)}</ul>)
      bullets = []
    }
  }
  lines.forEach((raw, idx) => {
    if (raw.startsWith('## ')) { flush(`u${idx}`); out.push(<h4 key={idx}>{raw.slice(3)}</h4>) }
    else if (raw.startsWith('CODE|')) { flush(`u${idx}`); out.push(<pre key={idx} className="code">{raw.slice(5).replace(/\\n/g, '\n')}</pre>) }
    else if (raw.startsWith('- ')) { bullets.push(raw.slice(2)) }
    else if (raw.trim() === '') { flush(`u${idx}`) }
    else { flush(`u${idx}`); out.push(<p key={idx}>{raw}</p>) }
  })
  flush('uend')
  return out
}

export function Detail() {
  const { detailId, progOf, go, toggleFav, isFav, openModal, toast, addComment, loadDetail, me } = useApp()
  const [tab, setTab] = useState<Tab>('desc')
  const [draft, setDraft] = useState('')
  const [deploy, setDeploy] = useState<DeploymentResponse | null>(null)

  useEffect(() => { if (detailId) loadDetail(detailId) }, [detailId, loadDetail])
  useEffect(() => {
    setDeploy(null)
    if (USE_API && detailId) {
      api.deploymentOf(detailId).then((d) => setDeploy(d ?? null)).catch(() => setDeploy(null))
    }
  }, [detailId])

  const p = detailId ? progOf(detailId) : null
  if (!p) {
    return (
      <div className="page container">
        <div className="empty">
          <div className="em-ico"><Icon name="info" size={26} /></div>
          <div className="em-t">프로그램을 찾을 수 없습니다.</div>
          <button className="btn btn-sm" onClick={() => go('list')}>목록으로</button>
        </div>
      </div>
    )
  }

  const cat = catOf(p.cat)
  const fav = isFav(p.id)
  const comments = p.comments

  const copy = (text: string, label: string) => {
    navigator.clipboard?.writeText(text).then(
      () => toast(`${label}을(를) 복사했습니다.`, 'ok'),
      () => toast('복사에 실패했습니다.', 'warn'),
    )
  }
  const submitComment = () => {
    if (!draft.trim()) return
    addComment(p.id, { user: me.name, dept: me.dept, body: draft.trim() })
    setDraft('')
  }

  return (
    <>
      <div className="detail-hero">
        <div className="container">
          <div className="breadcrumb"><span onClick={() => go('home')}>홈</span> · <span onClick={() => go('list')}>프로그램 탐색</span> · {cat.name}</div>
          <div className="dh-top">
            <div className="dh-icon"><Icon name={cat.icon} size={24} /></div>
            <div className="dh-main">
              <div className="dh-title">
                {p.name}
                <span className="badge badge-blue">{cat.name}</span>
                {p.status !== 'public' && <StatusBadge status={p.status} />}
              </div>
              <div className="dh-sub">{p.summary}</div>
              <div className="dh-idline">
                <span>{p.owner} · {p.dept}</span>
                <span className="repo-inline">{repoName(p.repo)}</span>
                <span className="ver-chip">v{p.ver}</span>
              </div>
              <div className="dh-stats">
                <span><Icon name="eye" size={14} /><b>{num(p.views)}</b> 조회</span>
                <span><Icon name="download" size={14} /><b>{num(p.downloads)}</b> 다운로드</span>
                <span><Icon name="star" size={14} /><b>{num(p.likes)}</b> 즐겨찾기</span>
                <span><Icon name="calendar" size={14} />최근 업데이트 {dot(p.updated)}</span>
              </div>
            </div>
            <div className="dh-actions">
              {deploy?.status === 'running' && deploy.url ? (
                <a className="btn btn-lg btn-primary" href={deploy.url} target="_blank" rel="noreferrer">
                  <Icon name="external" size={16} /> 웹에서 바로 사용
                </a>
              ) : (
                <button className="btn btn-lg btn-primary" onClick={() => openModal(<RunModal p={p} />)}>
                  <Icon name="web" size={16} /> 바로 사용하기
                </button>
              )}
              <button className={`btn ${fav ? 'btn-ok' : ''}`} onClick={() => toggleFav(p.id)}>
                <Icon name={fav ? 'star-filled' : 'star'} size={15} /> {fav ? '즐겨찾기 됨' : '즐겨찾기'}
              </button>
              <div className="dh-hint">내부망 전용 · 개인정보 처리 시 주의</div>
            </div>
          </div>
          <div className="tabs">
            <button className={tab === 'desc' ? 'on' : ''} onClick={() => setTab('desc')}>소개</button>
            <button className={tab === 'readme' ? 'on' : ''} onClick={() => setTab('readme')}>사용 방법</button>
            <button className={tab === 'history' ? 'on' : ''} onClick={() => setTab('history')}>업데이트 내역</button>
            <button className={tab === 'comments' ? 'on' : ''} onClick={() => setTab('comments')}>의견 {comments.length > 0 && `(${comments.length})`}</button>
          </div>
        </div>
      </div>

      <div className="container">
        <div className="detail-layout">
          <div>
            {tab === 'desc' && (
              <div>
                <div className="side-sec">주요 기능</div>
                <div className="feat-list">
                  {featuresOf(p).map((f, i) => (
                    <div key={i} className="feat"><span className="ft-no">{i + 1}</span><div className="ft-t">{f}</div></div>
                  ))}
                </div>
                <div className="side-sec">상세 설명</div>
                <p className="readme" style={{ marginBottom: 20 }}>{p.desc}</p>
                <div className="side-sec">제공 방식</div>
                <div className="use-list">
                  {runOf(p).map((id) => {
                    const rt = runTypeOf(id)
                    return (
                      <div key={id} className="use-item">
                        <span className="ui-ico"><Icon name={rt.icon} size={15} /></span>
                        <div><div className="ui-t">{rt.name}</div><div className="ui-d">{rt.desc}</div></div>
                      </div>
                    )
                  })}
                </div>
                <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
                  <PurposeBadges p={p} /><TechLine p={p} max={99} />
                </div>
              </div>
            )}

            {tab === 'readme' && <div className="readme">{renderReadme(p.readme)}</div>}

            {tab === 'history' && (
              <div>
                {p.history.map((h) => (
                  <div key={h.ver} className="hist-row">
                    <div className="hist-ver"><span className="ver-chip">v{h.ver}</span><div className="hist-date">{dot(h.date)}</div></div>
                    <div className="hist-log">{h.log}</div>
                  </div>
                ))}
              </div>
            )}

            {tab === 'comments' && (
              <div>
                <div className="comment-form">
                  <textarea className="textarea" placeholder="이 프로그램에 대한 의견을 남겨 주세요." value={draft} onChange={(e) => setDraft(e.target.value)} />
                  <button className="btn btn-primary" onClick={submitComment}>등록</button>
                </div>
                {comments.length === 0 ? (
                  <div className="empty"><div className="em-t">아직 등록된 의견이 없습니다.</div><div>첫 의견을 남겨 보세요.</div></div>
                ) : comments.map((c, i) => (
                  <div key={i} className="comment">
                    <div className="c-avatar">{initialOf(c.user)}</div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div className="c-head"><span className="c-name">{c.user}</span><span className="c-dept">{c.dept}</span><span className="c-time">{dot(c.time)}</span></div>
                      <div className="c-body">{c.body}</div>
                      {c.reply && (
                        <div className="c-reply">
                          <div className="c-head"><span className="c-name">{c.reply.user}</span><span className="c-dept">{c.reply.dept}</span><span className="c-time">{dot(c.reply.time)}</span></div>
                          <div className="c-body">{c.reply.body}</div>
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <aside>
            <div className="panel" style={{ marginBottom: 16 }}>
              <div className="panel-body">
                <div className="side-sec">정보</div>
                <div className="meta-list">
                  <div className="meta-row"><span className="k">업무 분야</span><span className="v">{cat.name}</span></div>
                  <div className="meta-row"><span className="k">등록자</span><span className="v">{p.owner} · {p.dept}</span></div>
                  <div className="meta-row"><span className="k">버전</span><span className="v">v{p.ver}</span></div>
                  <div className="meta-row"><span className="k">공개 범위</span><span className="v">{SCOPE_SHORT[p.scope]}</span></div>
                  <div className="meta-row"><span className="k">상태</span><span className="v">{statusLabel(p.status)}</span></div>
                  <div className="meta-row"><span className="k">등록일</span><span className="v">{dot(p.created)}</span></div>
                </div>
              </div>
            </div>

            <div className="panel" style={{ marginBottom: 16 }}>
              <div className="panel-body">
                <div className="side-sec">저장소 (branch: {branchOf(p)})</div>
                <div className="repo-box" style={{ marginBottom: 8 }}>
                  <span className="u">{p.repo}</span>
                  <button onClick={() => copy(p.repo, '저장소 주소')} title="주소 복사"><Icon name="copy" size={14} /></button>
                </div>
                <div className="repo-box">
                  <span className="u">{cloneCmd(p)}</span>
                  <button onClick={() => copy(cloneCmd(p), 'clone 명령')} title="명령 복사"><Icon name="copy" size={14} /></button>
                </div>
              </div>
            </div>

            {p.files.length > 0 && (
              <div className="panel">
                <div className="panel-body">
                  <div className="side-sec">첨부 파일</div>
                  {p.files.map((f) => (
                    <div key={f.name} className="file-row">
                      <span className="file-ico"><Icon name="file" size={14} /></span>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div className="file-name">{f.name}</div>
                        <div className="file-size">{f.type} · {f.size}</div>
                      </div>
                      <button className="btn btn-sm" onClick={() => toast('[시연용] 다운로드는 실제 서비스에서 제공됩니다.', 'info')}><Icon name="download" size={14} /></button>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </aside>
        </div>
      </div>
    </>
  )
}
