import { useEffect, useState } from 'react'
import { Icon } from '../../icons/Icon'
import { runOf, runTypeOf, repoName } from '../../lib/helpers'
import { useApp } from '../../state/AppContext'
import { USE_API, api, type DeploymentResponse } from '../../api/client'
import type { Program } from '../../types'

export function RunModal({ p }: { p: Program }) {
  const { closeModal, toast } = useApp()
  const runs = runOf(p)
  const [deploy, setDeploy] = useState<DeploymentResponse | null>(null)

  useEffect(() => {
    if (USE_API) {
      api.deploymentOf(p.id).then((d) => setDeploy(d ?? null)).catch(() => setDeploy(null))
    }
  }, [p.id])

  const running = deploy?.status === 'running' && !!deploy?.url
  const webUrl = running ? deploy!.url! : null

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
        {webUrl && (
          <a
            className="btn btn-lg btn-primary"
            href={webUrl}
            target="_blank"
            rel="noreferrer"
            onClick={closeModal}
            style={{ display: 'flex', width: '100%', justifyContent: 'center', gap: 8, marginBottom: 12 }}
          >
            <Icon name="external" size={16} /> 웹에서 바로 사용
          </a>
        )}
        <div className="run-list">
          {runs.map((id) => {
            const rt = runTypeOf(id)
            // 배포되어 실행 중이면 '웹에서 바로 사용' 옵션은 실제 서비스 주소로 연결
            if (id === 'web' && webUrl) {
              return (
                <a
                  key={id}
                  className="run-opt"
                  href={webUrl}
                  target="_blank"
                  rel="noreferrer"
                  onClick={closeModal}
                >
                  <span className="ro-ico"><Icon name={rt.icon} size={16} /></span>
                  <span>
                    <span className="ro-t">{rt.name}</span>
                    <span className="ro-d">브라우저에서 바로 실행합니다.</span>
                  </span>
                  <span className="ro-mark"><Icon name="external" size={14} /></span>
                </a>
              )
            }
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
