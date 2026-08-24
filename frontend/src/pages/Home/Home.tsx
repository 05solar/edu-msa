import './Home.css'
import { useState } from 'react'
import { CATEGORIES } from '../../data/catalog'
import { num } from '../../lib/helpers'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import { ProgramRow } from '../../components/program/ProgramRow'
import { RunModal } from '../../components/RunModal/RunModal'
import type { CategoryId } from '../../types'

const QUICK = ['회의록 요약', '공문 검토', '예산 집행', '설문 집계', 'HWP 자동화']

export function Home() {
  const { publicPrograms, favPrograms, go, setFilters, progOf, openModal, setMyTab } = useApp()
  const [q, setQ] = useState('')

  const search = (term?: string) => {
    setFilters({ q: term ?? q, cat: 'all', purposes: [], tech: [] })
    go('list')
  }
  const goCat = (cat: CategoryId) => { setFilters({ cat, q: '', purposes: [], tech: [] }); go('list') }

  const catCount = (id: CategoryId) => publicPrograms.filter((p) => p.cat === id).length

  const popular = [...publicPrograms].sort((a, b) => b.views - a.views).slice(0, 8)
  const recent = [...publicPrograms].sort((a, b) => b.updated.localeCompare(a.updated)).slice(0, 4)

  const onRun = (id: number) => { const p = progOf(id); if (p) openModal(<RunModal p={p} />) }

  return (
    <>
      <section className="hero">
        <div className="container">
          <h1>업무에 필요한 프로그램을<br /><span className="hl">검색 한 번</span>으로 찾아 바로 사용하세요.</h1>
          <div className="hero-search">
            <div className="hs-field">
              <span className="ico"><Icon name="search" size={16} /></span>
              <input
                placeholder="어떤 업무를 도와드릴까요? (예: 회의록 요약)"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') search() }}
              />
            </div>
            <button className="btn btn-primary" onClick={() => search()}>검색</button>
          </div>
          <div className="quick-tags">
            <span className="lbl">추천 검색어</span>
            {QUICK.map((t) => <button key={t} onClick={() => search(t)}>{t}</button>)}
          </div>
        </div>
      </section>

      <div className="container">
        <section className="section">
          <div className="section-head">
            <div>
              <h2>업무 분야</h2>
              <div className="sh-desc">가장 먼저 업무 분야로 찾아보세요.</div>
            </div>
            <button className="link-more" onClick={() => go('list')}>전체 보기</button>
          </div>
          <div className="cat-grid">
            {CATEGORIES.map((c) => (
              <button key={c.id} className="cat-card" onClick={() => goCat(c.id)}>
                <span className="cc-icon" style={{ color: c.color }}><Icon name={c.icon} size={30} /></span>
                <div className="cc-name">{c.name}</div>
                <div className="cc-count">{catCount(c.id)}개</div>
              </button>
            ))}
          </div>
        </section>

        {favPrograms.length > 0 && (
          <section className="section">
            <div className="section-head">
              <div><h2>즐겨찾는 프로그램</h2><div className="sh-desc">내가 즐겨찾기한 프로그램입니다.</div></div>
              <button className="link-more" onClick={() => { setMyTab('fav'); go('my') }}>더 보기</button>
            </div>
            <div className="prog-rows">
              {favPrograms.slice(0, 5).map((p) => <ProgramRow key={p.id} p={p} onRun={onRun} />)}
            </div>
          </section>
        )}

        <section className="section">
          <div className="section-head"><div><h2>인기 순위</h2><div className="sh-desc">조회수 기준 인기 프로그램입니다.</div></div></div>
          <div className="rank-list">
            {popular.map((p, i) => (
              <div key={p.id} className={`rank-item${i < 3 ? ' top' : ''}`} onClick={() => go('detail', p.id)}>
                <span className="rank-no">{i + 1}</span>
                <div className="ri-body">
                  <div className="ri-name">{p.name}</div>
                  <div className="ri-sub">{p.owner} · {p.dept}</div>
                </div>
                <span className="ri-metric"><Icon name="eye" size={13} />{num(p.views)}</span>
              </div>
            ))}
          </div>
        </section>

        <section className="section">
          <div className="section-head">
            <div><h2>최근 업데이트</h2></div>
            <button className="link-more" onClick={() => { setFilters({ sort: 'latest' }); go('list') }}>더 보기</button>
          </div>
          <div className="prog-rows">
            {recent.map((p) => <ProgramRow key={p.id} p={p} onRun={onRun} />)}
          </div>
        </section>

        <div className="home-note">
          <Icon name="info" size={16} />
          <span><b>내가 만든 프로그램도 공유할 수 있어요.</b> GitHub 레포 주소만 등록하면 새 서비스로 배포됩니다.</span>
          <button className="btn btn-sm btn-primary" onClick={() => go('register')}>프로그램 등록</button>
        </div>
      </div>
    </>
  )
}
