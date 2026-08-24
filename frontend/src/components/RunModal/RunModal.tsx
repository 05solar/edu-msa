import { Icon } from '../../icons/Icon'
import { runOf, runTypeOf, repoName } from '../../lib/helpers'
import { useApp } from '../../state/AppContext'
import type { Program } from '../../types'

export function RunModal({ p }: { p: Program }) {
  const { closeModal, toast } = useApp()
  const runs = runOf(p)

  const onRun = (name: string) => {
    closeModal()
    toast(`[시연용] "${name}" 방식은 실제 서비스에서 연결됩니다.`, 'info')
  }

  return (
    <div className="modal">
      <div className="modal-head">
        <div>
          <h3>바로 사용하기</h3>
          <div className="mh-sub">{p.name} · {repoName(p.repo)}</div>
        </div>
        <button className="modal-close" onClick={closeModal} aria-label="닫기"><Icon name="close" size={18} /></button>
      </div>
      <div className="modal-body">
        <div className="run-list">
          {runs.map((id) => {
            const rt = runTypeOf(id)
            return (
              <button key={id} className="run-opt" onClick={() => onRun(rt.name)}>
                <span className="ro-ico"><Icon name={rt.icon} size={16} /></span>
                <span>
                  <span className="ro-t">{rt.name}</span>
                  <span className="ro-d">{rt.desc}</span>
                </span>
                <span className="ro-mark"><Icon name="chevron-right" size={14} /></span>
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}
