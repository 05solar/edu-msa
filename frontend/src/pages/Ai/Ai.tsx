import './Ai.css'
import { useMemo, useRef, useState } from 'react'
import { AI_MODELS } from '../../data/catalog'
import { catOf, techOf } from '../../lib/helpers'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import type { Program } from '../../types'

interface Msg { who: 'ai' | 'me'; text: string; recs?: number[] }
const SUGGESTIONS = ['회의록을 정리하고 싶어요', '공문 표기를 점검하고 싶어요', '예산 집행 현황을 보고 싶어요', '설문 결과를 집계하고 싶어요']

export function Ai() {
  const { publicPrograms, go, me } = useApp()
  const [msgs, setMsgs] = useState<Msg[]>([
    { who: 'ai', text: `안녕하세요, ${me.name} 님. 어떤 업무를 도와드릴까요? 하시려는 일을 문장으로 설명해 주시면 적합한 프로그램을 추천해 드립니다.` },
  ])
  const [input, setInput] = useState('')
  const [model, setModel] = useState(AI_MODELS[0].id)
  const areaRef = useRef<HTMLDivElement>(null)

  const recommend = useMemo(() => (q: string): Program[] => {
    const terms = q.toLowerCase().split(/\s+/).filter(Boolean)
    const scored = publicPrograms.map((p) => {
      const hay = [p.name, p.summary, p.desc, ...p.tags, ...techOf(p)].join(' ').toLowerCase()
      const score = terms.reduce((s, t) => s + (hay.includes(t) ? 1 : 0), 0)
      return { p, score }
    }).filter((x) => x.score > 0).sort((a, b) => b.score - a.score)
    return (scored.length ? scored : publicPrograms.map((p) => ({ p, score: p.views })).sort((a, b) => b.score - a.score))
      .slice(0, 3).map((x) => x.p)
  }, [publicPrograms])

  const send = (text: string) => {
    const q = text.trim()
    if (!q) return
    const recs = recommend(q)
    setMsgs((prev) => [
      ...prev,
      { who: 'me', text: q },
      { who: 'ai', text: recs.length ? '설명해 주신 업무에 맞는 프로그램을 찾았습니다. 아래에서 확인해 보세요.' : '적합한 프로그램을 찾지 못했습니다. 다른 표현으로 다시 설명해 주세요.', recs: recs.map((p) => p.id) },
    ])
    setInput('')
    window.setTimeout(() => { areaRef.current?.scrollTo({ top: areaRef.current.scrollHeight }) }, 30)
  }

  return (
    <div className="page container">
      <div className="page-head">
        <div className="page-title">AI로 프로그램 찾기</div>
        <div className="page-desc">하시려는 업무를 설명하면 적합한 프로그램을 추천합니다. (내부망 전용 모델)</div>
      </div>

      <div className="ai-layout">
        <div className="panel chat-panel">
          <div className="panel-body chat-panel" style={{ padding: 18 }}>
            <div className="chat-area" ref={areaRef}>
              {msgs.map((m, i) => (
                <div key={i} className={`chat-msg ${m.who}`}>
                  <div className={`chat-av ${m.who}`}>{m.who === 'ai' ? 'AI' : me.name.slice(0, 2)}</div>
                  <div style={{ maxWidth: '78%' }}>
                    <div className="chat-bubble">{m.text}</div>
                    {m.recs && m.recs.length > 0 && (
                      <div className="chat-recs">
                        {m.recs.map((id) => {
                          const p = publicPrograms.find((x) => x.id === id)!
                          const cat = catOf(p.cat)
                          return (
                            <button key={id} className="chat-rec" onClick={() => go('detail', id)}>
                              <span className="cr-ico"><Icon name={cat.icon} size={16} /></span>
                              <div style={{ minWidth: 0 }}>
                                <div className="cr-t">{p.name}</div>
                                <div className="cr-d">{p.summary}</div>
                              </div>
                              <Icon name="chevron-right" size={15} />
                            </button>
                          )
                        })}
                      </div>
                    )}
                  </div>
                </div>
              ))}
              {msgs.length <= 1 && (
                <div className="chat-suggest">
                  {SUGGESTIONS.map((s) => <button key={s} onClick={() => send(s)}>{s}</button>)}
                </div>
              )}
            </div>
            <div className="chat-input">
              <input className="input" placeholder="예: 회의 녹음을 요약해서 정리하고 싶어요"
                value={input} onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') send(input) }} />
              <button className="btn btn-primary" onClick={() => send(input)}><Icon name="send" size={15} /> 보내기</button>
            </div>
          </div>
        </div>

        <aside>
          <div className="panel">
            <div className="panel-head"><div className="panel-title">AI 설정</div></div>
            <div className="panel-body">
              <div className="field" style={{ marginBottom: 0 }}>
                <label>모델</label>
                <select className="select" value={model} onChange={(e) => setModel(e.target.value)}>
                  {AI_MODELS.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}
                </select>
                <div className="hint">{AI_MODELS.find((m) => m.id === model)?.sub}</div>
              </div>
              <div className="notice-inline" style={{ marginTop: 16, marginBottom: 0 }}>
                <Icon name="info" size={15} />
                <span>추천은 공개된 프로그램의 소개·태그·기술을 기준으로 합니다. 외부 API는 사용하지 않습니다.</span>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  )
}
