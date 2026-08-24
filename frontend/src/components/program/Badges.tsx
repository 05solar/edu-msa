import { Icon } from '../../icons/Icon'
import { purposeOf, purposesOf, techOf, statusLabel, statusBadgeClass } from '../../lib/helpers'
import type { Program } from '../../types'

export function PurposeBadges({ p, plain }: { p: Program; plain?: boolean }) {
  const ids = purposesOf(p)
  if (!ids.length) return null
  return (
    <span className="purpose-set">
      {ids.map((id) => {
        const pu = purposeOf(id)
        return (
          <span key={id} className={`purpose${plain ? ' plain' : ''}`}>
            <Icon name={pu.icon} size={12} />{pu.name}
          </span>
        )
      })}
    </span>
  )
}

export function TechLine({ p, max = 4 }: { p: Program; max?: number }) {
  const tech = techOf(p)
  if (!tech.length) return null
  const shown = tech.slice(0, max)
  const rest = tech.length - shown.length
  return (
    <span className="tech-line">
      <span className="tl-k">기술</span>
      {shown.map((t) => <span key={t} className="tech-chip">{t}</span>)}
      {rest > 0 && <span className="tl-v">외 {rest}</span>}
    </span>
  )
}

export function StatusBadge({ status }: { status: Program['status'] }) {
  return (
    <span className={`badge ${statusBadgeClass(status)}`}>
      <span className="dot" />{statusLabel(status)}
    </span>
  )
}
