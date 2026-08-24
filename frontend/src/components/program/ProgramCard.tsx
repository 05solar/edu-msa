import { Icon } from '../../icons/Icon'
import { catOf, num, repoName } from '../../lib/helpers'
import { useApp } from '../../state/AppContext'
import type { Program } from '../../types'
import { PurposeBadges } from './Badges'

export function ProgramCard({ p }: { p: Program }) {
  const { go } = useApp()
  const cat = catOf(p.cat)
  return (
    <div className="prog-card" onClick={() => go('detail', p.id)}>
      <div className="pc-top">
        <div className="pc-icon"><Icon name={cat.icon} size={19} /></div>
        <div className="pc-titlewrap">
          <div className="pc-title"><span className="name">{p.name}</span></div>
          <div className="pc-repo">{repoName(p.repo)}</div>
        </div>
      </div>
      <p className="pc-desc">{p.summary}</p>
      <div className="pc-tags"><PurposeBadges p={p} /></div>
      <div className="pc-foot">
        <div className="pc-meta">
          <span><span className="cat-dot" style={{ background: cat.color }} />{cat.name}</span>
          <span><Icon name="eye" size={13} />{num(p.views)}</span>
          <span><Icon name="download" size={13} />{num(p.downloads)}</span>
        </div>
        <span className="ver-chip">v{p.ver}</span>
      </div>
    </div>
  )
}
