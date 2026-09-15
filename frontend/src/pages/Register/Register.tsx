import './Register.css'
import { useEffect, useState } from 'react'
import { CATEGORIES, PURPOSES, RUN_TYPES, SCOPES } from '../../data/catalog'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import { USE_API, api, type GiteaAccountStatus, type GiteaRepo, type ValidationResult } from '../../api/client'
import { GuideModal } from '../../components/GuideModal/GuideModal'
import type { NewProgramInput } from '../../state/AppContext'
import type { PurposeId, RunTypeId, Scope } from '../../types'

const FLOW = [
  { t: 'Gitea 레포 등록', d: '표준 규격(service.yaml·Dockerfile)에 맞춘 내부 Gitea 레포 주소 입력' },
  { t: '규격 자동 검증', d: '필수 파일·포트·헬스 경로 정적 검사' },
  { t: '운영 관리자 검토', d: '승인 시 새 서비스로 배포' },
  { t: '공개', d: '전 직원 또는 부서에 공개' },
]
const GUIDE = [
  '레포 루트에 service.yaml(name·slug·category·port)이 있어야 합니다.',
  '레포 루트에 Dockerfile이 있어야 합니다.',
  '앱은 PORT 환경변수 포트로 열려야 합니다.',
  'GET /healthz 가 200을 반환해야 합니다.',
  '개인정보·민감정보를 레포에 커밋하지 않습니다.',
]

export function Register() {
  const { addProgram, go, toast, me, openModal } = useApp()
  const [f, setF] = useState<NewProgramInput>({
    name: '', summary: '', desc: '', cat: '', dept: me.dept, ver: '1.0.0',
    repo: '', branch: 'main', tags: [], purposes: [], run: ['gitea'], scope: 'all', readme: '',
  })
  const [tagInput, setTagInput] = useState('')
  const [validation, setValidation] = useState<ValidationResult | null>(null)
  const [validating, setValidating] = useState(false)
  const set = (patch: Partial<NewProgramInput>) => setF((prev) => ({ ...prev, ...patch }))

  // 내부 Gitea 연동 — 내 계정 상태와 레포 목록(있으면 드롭다운으로 주소 자동 입력)
  const [gitea, setGitea] = useState<GiteaAccountStatus | null>(null)
  const [repos, setRepos] = useState<GiteaRepo[]>([])
  const [reposLoading, setReposLoading] = useState(false)
  const loadRepos = () => {
    setReposLoading(true)
    api.giteaRepos().then(setRepos).catch(() => setRepos([])).finally(() => setReposLoading(false))
  }
  useEffect(() => {
    if (!USE_API) return
    api.giteaAccount().then((s) => {
      setGitea(s)
      if (s.enabled && s.issued) loadRepos()
    }).catch(() => { /* 미연동 환경 — 수동 입력만 제공 */ })
  }, [])
  const giteaHost = gitea?.host ?? 'gitea.edu.internal'

  const runValidate = () => {
    if (!USE_API) { toast('규격 검증은 백엔드 연동 모드(VITE_USE_API=true)에서 동작합니다.', 'warn'); return }
    if (!f.repo.trim()) { toast('레포 주소를 입력해 주세요.', 'warn'); return }
    setValidating(true)
    api.validateSpec(f.repo, f.branch)
      .then(setValidation)
      .catch((e) => toast('검증 실패: ' + (e as Error).message, 'warn'))
      .finally(() => setValidating(false))
  }

  const addTag = (v: string) => {
    const t = v.trim().replace(/,$/, '')
    if (t && !f.tags.includes(t)) set({ tags: [...f.tags, t] })
    setTagInput('')
  }
  const togglePurpose = (id: PurposeId) => set({
    purposes: f.purposes.includes(id) ? f.purposes.filter((x) => x !== id) : [...f.purposes, id],
  })
  const toggleRun = (id: RunTypeId) => set({
    run: f.run.includes(id) ? f.run.filter((x) => x !== id) : [...f.run, id],
  })

  const submit = () => {
    if (!f.name.trim()) return toast('프로그램 이름을 입력해 주세요.', 'warn')
    if (!f.cat) return toast('업무 분야를 선택해 주세요.', 'warn')
    if (!f.summary.trim()) return toast('한 줄 요약을 입력해 주세요.', 'warn')
    if (!/^https?:\/\/.+/.test(f.repo)) return toast('올바른 레포 주소(https://…)를 입력해 주세요.', 'warn')
    addProgram(f)
    go('my')
  }
  const saveDraft = () => toast('임시저장했습니다. (시연용)', 'info')

  return (
    <div className="page container">
      <div className="page-head">
        <div className="page-title">프로그램 등록</div>
        <div className="page-desc">내부 Gitea 레포 주소를 등록하면 규격 검증 후 운영 관리자 검토를 거쳐 새 서비스로 배포됩니다. (외부 GitHub 등은 사용할 수 없습니다)</div>
      </div>

      <div className="form-layout">
        <div className="panel">
          <div className="form-section">
            <div className="form-section-title"><span className="step-no">1</span> 기본 정보</div>
            <div className="form-section-desc">프로그램의 이름과 소개를 입력합니다.</div>
            <div className="field">
              <label>프로그램 이름<span className="req">*</span></label>
              <input className="input" value={f.name} onChange={(e) => set({ name: e.target.value })} placeholder="예: 출장 정산 자동 계산기" />
            </div>
            <div className="field">
              <label>한 줄 요약<span className="req">*</span></label>
              <input className="input" value={f.summary} onChange={(e) => set({ summary: e.target.value })} placeholder="이 프로그램이 어떤 업무를 돕는지 한 문장으로" />
            </div>
            <div className="field">
              <label>상세 설명</label>
              <textarea className="textarea" value={f.desc} onChange={(e) => set({ desc: e.target.value })} placeholder="기능·사용 대상·처리 방식 등을 설명합니다." />
            </div>
            <div className="grid-2">
              <div className="field">
                <label>업무 분야<span className="req">*</span></label>
                <select className="select" value={f.cat} onChange={(e) => set({ cat: e.target.value })}>
                  <option value="">선택</option>
                  {CATEGORIES.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </div>
              <div className="field">
                <label>담당 부서</label>
                <input className="input" value={f.dept} onChange={(e) => set({ dept: e.target.value })} />
              </div>
            </div>
          </div>

          <div className="form-section">
            <div className="form-section-title"><span className="step-no">2</span> 분류</div>
            <div className="form-section-desc">기능 유형과 사용 기술을 선택합니다.</div>
            <div className="field">
              <label>기능 유형</label>
              <div className="tag-cloud">
                {PURPOSES.map((p) => (
                  <button key={p.id} className={`tag-btn${f.purposes.includes(p.id) ? ' on' : ''}`} onClick={() => togglePurpose(p.id)}>{p.name}</button>
                ))}
              </div>
            </div>
            <div className="field">
              <label>기술 태그</label>
              <div className="tag-input-wrap">
                {f.tags.map((t, i) => (
                  <span key={t} className="tag-pill">{t}
                    <button onClick={() => set({ tags: f.tags.filter((_, idx) => idx !== i) })} aria-label="제거"><Icon name="close" size={12} /></button>
                  </span>
                ))}
                <input value={tagInput} onChange={(e) => setTagInput(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ',') { e.preventDefault(); addTag(tagInput) } }}
                  placeholder="입력 후 Enter (예: Python)" />
              </div>
            </div>
          </div>

          <div className="form-section">
            <div className="form-section-title"><span className="step-no">3</span> Gitea 저장소</div>
            <div className="form-section-desc">내부 Gitea 에 올린 레포 주소를 입력합니다. 비공개 레포는 <code>edu-deploy-bot</code> 을 협업자(읽기)로 초대해야 배포됩니다.</div>
            <div className="notice-inline">
              <Icon name="info" size={16} />
              <span>등록 전 <b>바이브 코딩 가이드</b>와 <b>표준 서비스 규격</b>을 확인하세요. service.yaml·Dockerfile·PORT·/healthz 규칙을 지켜야 배포됩니다.</span>
            </div>
            {gitea?.enabled && !gitea.issued && (
              <div className="notice-inline" style={{ borderColor: 'var(--warn, #d9a441)' }}>
                <Icon name="warn" size={16} />
                <span>아직 <b>Gitea 계정이 없습니다.</b> 마이페이지에서 계정을 발급받고 코드를 올린 뒤 등록하세요.{' '}
                  <button className="btn btn-sm" style={{ marginLeft: 6 }} onClick={() => go('my')}>마이페이지로 이동</button>
                </span>
              </div>
            )}
            {gitea?.enabled && gitea.issued && (
              <div className="field">
                <label>내 Gitea 레포에서 선택</label>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <select
                    className="input"
                    style={{ flex: 1 }}
                    value={repos.some((r) => r.url === f.repo) ? f.repo : ''}
                    onChange={(e) => { if (e.target.value) set({ repo: e.target.value }) }}
                  >
                    <option value="">
                      {reposLoading ? '레포 목록 불러오는 중…'
                        : repos.length === 0 ? '레포가 없습니다 — Gitea 에 코드를 먼저 올리세요'
                        : `— 레포 선택 (${repos.length}개) —`}
                    </option>
                    {repos.map((r) => (
                      <option key={r.url} value={r.url}>{r.name}{r.isPrivate ? ' (비공개)' : ''}</option>
                    ))}
                  </select>
                  <button className="btn btn-sm" disabled={reposLoading} onClick={loadRepos} title="Gitea 레포 목록 새로고침">
                    <Icon name="verify" size={13} /> 새로고침
                  </button>
                </div>
                <div className="hint">선택하면 아래 레포 주소가 자동 입력됩니다. 방금 push 한 레포가 안 보이면 새로고침을 누르세요.</div>
              </div>
            )}
            <div className="grid-2">
              <div className="field" style={{ gridColumn: '1 / span 1' }}>
                <label>레포 주소<span className="req">*</span></label>
                <input className="input" value={f.repo} onChange={(e) => set({ repo: e.target.value })} placeholder={`https://${giteaHost}/내계정/my-tool`} />
              </div>
              <div className="field">
                <label>브랜치</label>
                <input className="input" value={f.branch} onChange={(e) => set({ branch: e.target.value })} placeholder="main" />
              </div>
            </div>
            <div className="field">
              <label>버전</label>
              <input className="input" value={f.ver} onChange={(e) => set({ ver: e.target.value })} placeholder="1.0.0" />
              <div className="hint">오프라인 시험용: 레포 주소에 <code>sample://travel-settlement</code> 을 넣고 규격 검증을 눌러보세요.</div>
            </div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <button className="btn btn-sm" disabled={validating} onClick={runValidate}>
                <Icon name="verify" size={14} /> {validating ? '검증 중…' : '레포 규격 검증'}
              </button>
            </div>
            {validation && (
              <div className="reject-editor" style={validation.valid
                ? { border: '1px solid #A9DCC4', background: '#F3FBF6', marginTop: 12 }
                : { marginTop: 12 }}>
                <h5 style={{ color: validation.valid ? 'var(--ok)' : 'var(--danger)' }}>
                  <Icon name={validation.valid ? 'check' : 'warn'} size={13} />{' '}
                  {validation.valid ? '표준 규격 통과' : '규격 오류 발견'}
                  <span style={{ fontWeight: 400, color: 'var(--ink-400)', marginLeft: 6 }}>· {validation.resolvedFrom}</span>
                </h5>
                {validation.valid && validation.spec ? (
                  <p style={{ margin: 0 }}>
                    slug: <b>{validation.spec.slug}</b> · 분야: {validation.spec.category} ·
                    포트: {validation.spec.port} · 헬스: {validation.spec.health}
                  </p>
                ) : (
                  <ul style={{ margin: 0, paddingLeft: 16 }}>
                    {validation.errors.map((e, i) => <li key={i} style={{ fontSize: 12.5, color: 'var(--ink-700)' }}>{e}</li>)}
                  </ul>
                )}
              </div>
            )}
          </div>

          <div className="form-section">
            <div className="form-section-title"><span className="step-no">4</span> 제공 방식</div>
            <div className="form-section-desc">사용자가 이 프로그램을 어떻게 사용할지 선택합니다.</div>
            <div className="run-toggle-grid">
              {RUN_TYPES.map((r) => (
                <label key={r.id} className={`run-toggle${f.run.includes(r.id) ? ' on' : ''}`}>
                  <input type="checkbox" checked={f.run.includes(r.id)} onChange={() => toggleRun(r.id)} />
                  <Icon name={r.icon} size={15} />{r.name}
                </label>
              ))}
            </div>
          </div>

          <div className="form-section">
            <div className="form-section-title"><span className="step-no">5</span> 공개 범위</div>
            <div className="form-section-desc">누구에게 공개할지 선택합니다.</div>
            {(Object.keys(SCOPES) as Scope[]).map((s) => (
              <label key={s} className={`radio-card${f.scope === s ? ' on' : ''}`}>
                <input type="radio" checked={f.scope === s} onChange={() => set({ scope: s })} />
                <div>
                  <div className="rc-t">{s === 'all' ? '전체 공개' : '부서 공개'}</div>
                  <div className="rc-d">{SCOPES[s]}</div>
                </div>
              </label>
            ))}
          </div>

          <div className="form-actions">
            <button className="btn" onClick={saveDraft}>임시저장</button>
            <button className="btn btn-primary" onClick={submit}>등록 요청</button>
          </div>
        </div>

        <aside>
          <button className="btn btn-navy guide-open-btn" style={{ width: '100%', marginBottom: 16 }}
            onClick={() => openModal(<GuideModal />)}>
            <Icon name="info" size={16} /> 처음이신가요? 등록 가이드 자세히 보기
          </button>
          <div className="panel" style={{ marginBottom: 16 }}>
            <div className="panel-head"><div className="panel-title">등록 절차</div></div>
            <div className="panel-body">
              <div className="flow-steps">
                {FLOW.map((s, i) => (
                  <div key={i} className="flow-step">
                    <span className="fs-no">{i + 1}</span>
                    <div><div className="fs-t">{s.t}</div><div className="fs-d">{s.d}</div></div>
                  </div>
                ))}
              </div>
            </div>
          </div>
          <div className="panel">
            <div className="panel-head"><div className="panel-title">등록 전 체크리스트</div></div>
            <div className="panel-body">
              <div className="guide-box"><ul>{GUIDE.map((g) => <li key={g}>{g}</li>)}</ul></div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  )
}
