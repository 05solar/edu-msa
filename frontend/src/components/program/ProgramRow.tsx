import { Icon } from '../../icons/Icon'
import { catOf, dot, num } from '../../lib/helpers'
import { useApp } from '../../state/AppContext'
import type { Program } from '../../types'
import { PurposeBadges, TechLine } from './Badges'

export function ProgramRow({ p, onRun }: { p: Program; onRun?: (id: number) => void }) {
  const { go } = useApp()
  const cat = catOf(p.cat)
  return (
    <div className="prog-row" onClick={() => go('detail', p.id)}>
      <div className="pr-icon"><Icon name={cat.icon} size={18} /></div>
      <div className="pr-body">
        <div className="pr-line1">
          <span className="pr-name">{p.name}</span>
          <span className="badge badge-blue">{cat.name}</span>
        </div>
        <p className="pr-summary">{p.summary}</p>
        <div className="pr-class">
          <PurposeBadges p={p} />
          <TechLine p={p} />
        </div>
        <div className="pr-meta">
          <span className="mg">{p.owner} · {p.dept}</span>
          <span className="mg"><Icon name="calendar" size={12} />{dot(p.updated)}</span>
          <span className="mg"><Icon name="eye" size={12} />{num(p.views)}</span>
          <span className="mg"><Icon name="download" size={12} />{num(p.downloads)}</span>
          <span className="mg">v{p.ver}</span>
        </div>
      </div>
      <div className="pr-actions" onClick={(e) => e.stopPropagation()}>
        <button className="btn btn-sm btn-primary" onClick={() => go('detail', p.id)}>상세 보기</button>
        <button className="btn btn-sm" onClick={() => onRun?.(p.id)}>바로 사용</button>
      </div>
    </div>
  )
}
