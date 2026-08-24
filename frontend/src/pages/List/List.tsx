import './List.css'
import { useMemo } from 'react'
import { CATEGORIES, PURPOSES, TECHS, SCOPE_SHORT } from '../../data/catalog'
import { purposeOf, catOf, techOf } from '../../lib/helpers'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import { ProgramRow } from '../../components/program/ProgramRow'
import { RunModal } from '../../components/RunModal/RunModal'
import type { PurposeId, Scope } from '../../types'

const SORTS: { id: 'latest' | 'popular' | 'downloads'; name: string }[] = [
  { id: 'latest', name: '최신순' },
  { id: 'popular', name: '조회순' },
  { id: 'downloads', name: '다운로드순' },
]

export function List() {
  const { publicPrograms, filters, setFilters, resetFilters, progOf, openModal } = useApp()

  const filtered = useMemo(() => {
    let arr = publicPrograms.filter((p) => {
      if (filters.cat !== 'all' && p.cat !== filters.cat) return false
      if (filters.purposes.length && !filters.purposes.every((x) => p.purposes.includes(x))) return false
      if (filters.tech.length && !filters.tech.every((t) => techOf(p).includes(t))) return false
      if (filters.scope !== 'any' && p.scope !== filters.scope) return false
      if (filters.q.trim()) {
        const q = filters.q.trim().toLowerCase()
        const hay = [p.name, p.summary, p.desc, ...p.tags, ...techOf(p)].join(' ').toLowerCase()
        if (!hay.includes(q)) return false
      }
      return true
    })
    arr = [...arr].sort((a, b) => {
      if (filters.sort === 'popular') return b.views - a.views
      if (filters.sort === 'downloads') return b.downloads - a.downloads
      return b.updated.localeCompare(a.updated)
    })
    return arr
  }, [publicPrograms, filters])

  const catCount = (id: string) => publicPrograms.filter((p) => p.cat === id).length
  const togglePurpose = (id: PurposeId) => setFilters({
    purposes: filters.purposes.includes(id) ? filters.purposes.filter((x) => x !== id) : [...filters.purposes, id],
  })
  const toggleTech = (t: string) => setFilters({
    tech: filters.tech.includes(t) ? filters.tech.filter((x) => x !== t) : [...filters.tech, t],
  })
  const onRun = (id: number) => { const p = progOf(id); if (p) openModal(<RunModal p={p} />) }

  const hasActive = filters.cat !== 'all' || filters.purposes.length > 0 || filters.tech.length > 0
    || filters.scope !== 'all' || filters.q.trim().length > 0

  return (
    <div className="page container">
      <div className="page-head">
        <div className="page-title">프로그램 탐색</div>
        <div className="page-desc">업무 분야·기능 유형·기술로 원하는 프로그램을 찾습니다.</div>
      </div>

      <div className="list-layout">
        <aside className="filter-panel panel">
          <div className="filter-group">
            <div className="filter-search">
              <span className="ico"><Icon name="search" size={14} /></span>
              <input className="input" placeholder="프로그램 검색" value={filters.q}
                onChange={(e) => setFilters({ q: e.target.value })} />
            </div>
          </div>

          <div className="filter-group">
            <h4>업무 분야</h4>
            <label className="radio-row">
              <input type="radio" checked={filters.cat === 'all'} onChange={() => setFilters({ cat: 'all' })} />
              전체<span className="cnt">{publicPrograms.length}</span>
            </label>
            {CATEGORIES.map((c) => (
              <label key={c.id} className="radio-row">
                <input type="radio" checked={filters.cat === c.id} onChange={() => setFilters({ cat: c.id })} />
                {c.name}<span className="cnt">{catCount(c.id)}</span>
              </label>
            ))}
          </div>

          <div className="filter-group">
            <h4>기능 유형 <span className="fh-sub">복수 선택</span></h4>
            <div className="tag-cloud">
              {PURPOSES.map((p) => (
                <button key={p.id} className={`tag-btn${filters.purposes.includes(p.id) ? ' on' : ''}`}
                  onClick={() => togglePurpose(p.id)}>{p.name}</button>
              ))}
            </div>
          </div>

          <div className="filter-group">
            <h4>기술 <span className="fh-sub">복수 선택</span></h4>
            <div className="tag-cloud">
              {TECHS.map((t) => (
                <button key={t} className={`tag-btn tech${filters.tech.includes(t) ? ' on' : ''}`}
                  onClick={() => toggleTech(t)}>{t}</button>
              ))}
            </div>
          </div>

          <div className="filter-group">
            <h4>공개 범위</h4>
            <label className="radio-row">
              <input type="radio" checked={filters.scope === 'any'} onChange={() => setFilters({ scope: 'any' })} />
              전체
            </label>
            {(['all', 'dept'] as Scope[]).map((s) => (
              <label key={s} className="radio-row">
                <input type="radio" checked={filters.scope === s} onChange={() => setFilters({ scope: s })} />
                {SCOPE_SHORT[s]}
              </label>
            ))}
          </div>
        </aside>

        <div>
          <div className="list-toolbar">
            <div className="search-box">
              <span className="ico"><Icon name="search" size={15} /></span>
              <input className="input" placeholder="프로그램명·업무·기술로 검색" value={filters.q}
                onChange={(e) => setFilters({ q: e.target.value })} />
            </div>
            <div className="sort-tabs">
              {SORTS.map((s) => (
                <button key={s.id} className={filters.sort === s.id ? 'on' : ''}
                  onClick={() => setFilters({ sort: s.id })}>{s.name}</button>
              ))}
            </div>
          </div>

          <div className="result-bar">
            <div>총 <b>{filtered.length}</b>개</div>
            {hasActive && (
              <div className="active-filters">
                {filters.cat !== 'all' && (
                  <span className="chip-x">{catOf(filters.cat).name}
                    <button onClick={() => setFilters({ cat: 'all' })} aria-label="제거"><Icon name="close" size={12} /></button></span>
                )}
                {filters.purposes.map((id) => (
                  <span key={id} className="chip-x">{purposeOf(id).name}
                    <button onClick={() => togglePurpose(id)} aria-label="제거"><Icon name="close" size={12} /></button></span>
                ))}
                {filters.tech.map((t) => (
                  <span key={t} className="chip-x">{t}
                    <button onClick={() => toggleTech(t)} aria-label="제거"><Icon name="close" size={12} /></button></span>
                ))}
                <button className="btn btn-sm btn-ghost" onClick={resetFilters}>필터 초기화</button>
              </div>
            )}
          </div>

          {filtered.length === 0 ? (
            <div className="empty">
              <div className="em-ico"><Icon name="search" size={26} /></div>
              <div className="em-t">검색 결과가 없습니다.</div>
              <div>다른 업무 분야나 검색어로 다시 시도해 보세요.</div>
            </div>
          ) : (
            <div className="prog-rows">
              {filtered.map((p) => <ProgramRow key={p.id} p={p} onRun={onRun} />)}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
