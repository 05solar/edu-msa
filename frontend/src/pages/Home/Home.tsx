import './Home.css'
import { useState } from 'react'
import { CATEGORIES, PURPOSES } from '../../data/catalog'
import { num } from '../../lib/helpers'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import { ProgramCard } from '../../components/program/ProgramCard'
import { ProgramRow } from '../../components/program/ProgramRow'
import { RunModal } from '../../components/RunModal/RunModal'
import type { CategoryId, PurposeId } from '../../types'

const QUICK = ['회의록 요약', '공문 검토', '예산 집행', '설문 집계', 'HWP 자동화']

export function Home() {
  const { publicPrograms, go, setFilters, progOf, openModal } = useApp()
  const [q, setQ] = useState('')

  const search = (term?: string) => {
    setFilters({ q: term ?? q, cat: 'all', purposes: [], tech: [] })
    go('list')
  }
  const goCat = (cat: CategoryId) => { setFilters({ cat, q: '', purposes: [], tech: [] }); go('list') }
  const goPurpose = (id: PurposeId) => { setFilters({ purposes: [id], cat: 'all', q: '', tech: [] }); go('list') }

  const catCount = (id: CategoryId) => publicPrograms.filter((p) => p.cat === id).length
  const purposeCount = (id: PurposeId) => publicPrograms.filter((p) => p.purposes.includes(id)).length

  const popular = [...publicPrograms].sort((a, b) => b.views - a.views).slice(0, 8)
  const featured = [...publicPrograms].sort((a, b) => b.downloads - a.downloads).slice(0, 3)
  const recent = [...publicPrograms].sort((a, b) => b.updated.localeCompare(a.updated)).slice(0, 4)

  const onRun = (id: number) => { const p = progOf(id); if (p) openModal(<RunModal p={p} />) }

  return (
    <>
      <section className="hero">
        <div className="container">
          <span className="hero-kicker">교육청 내부 업무 전용</span>
          <h1>업무에 필요한 프로그램을<br /><span className="hl">검색 한 번</span>으로 찾아 바로 사용하세요.</h1>
          <p>동료가 만든 업무 자동화·문서 생성·데이터 분석 프로그램을 한 곳에서 찾고, 설치 없이
            바로 사용하거나 소스코드를 내려받을 수 있습니다.</p>
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
                <span className="cc-icon" style={{ color: c.color }}><Icon name={c.icon} size={18} /></span>
                <div className="cc-name">{c.name}</div>
                <div className="cc-count">{catCount(c.id)}개</div>
              </button>
            ))}
          </div>
        </section>

        <section className="section">
          <div className="section-head">
            <div><h2>기능 유형으로 찾기</h2><div className="sh-desc">이 프로그램이 업무를 어떻게 돕는지로 찾습니다.</div></div>
          </div>
          <div className="purpose-grid">
            {PURPOSES.map((p) => (
              <button key={p.id} className="purpose-card" onClick={() => goPurpose(p.id)}>
                <span className="pg-ico"><Icon name={p.icon} size={17} /></span>
                <div className="pg-name">{p.name}</div>
                <div className="pg-cnt">{purposeCount(p.id)}개</div>
              </button>
            ))}
          </div>
        </section>

        <section className="section">
          <div className="section-head">
            <div><h2>많이 찾는 프로그램</h2><div className="sh-desc">조회수 기준 인기 프로그램입니다.</div></div>
            <button className="link-more" onClick={() => { setFilters({ sort: 'popular' }); go('list') }}>더 보기</button>
          </div>
          <div className="card-grid">
            {featured.map((p) => <ProgramCard key={p.id} p={p} />)}
          </div>
        </section>

        <section className="section">
          <div className="section-head"><div><h2>인기 순위</h2></div></div>
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
