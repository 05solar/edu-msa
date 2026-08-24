import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import type { IconName } from '../../icons/Icon'

const KIND_ICON: Record<string, IconName> = { ok: 'check', warn: 'warn', info: 'info' }

export function Toasts() {
  const { toasts } = useApp()
  return (
    <div id="toast-wrap">
      {toasts.map((t) => (
        <div key={t.id} className={`toast ${t.kind}`}>
          <Icon name={KIND_ICON[t.kind]} size={15} />
          <span>{t.msg}</span>
        </div>
      ))}
    </div>
  )
}
