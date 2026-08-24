import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import type { DeploymentResponse } from '../../api/client'

const STATUS_BADGE: Record<string, string> = {
  running: 'badge-ok', failed: 'badge-danger', deploying: 'badge-warn',
  building: 'badge-warn', validating: 'badge-blue', pending: 'badge-gray',
}
const STATUS_LABEL: Record<string, string> = {
  running: '배포 완료(running)', failed: '실패(failed)', deploying: '배포 중',
  building: '빌드 중', validating: '검증 중', pending: '대기',
}

export function DeployResultModal({ res }: { res: DeploymentResponse }) {
  const { closeModal, toast } = useApp()
  return (
    <div className="modal wide">
      <div className="modal-head">
        <div>
          <h3>배포 결과 · {res.name ?? res.slug ?? '서비스'}</h3>
          <div className="mh-sub">mode={res.mode} · image={res.imageTag ?? '-'}</div>
        </div>
        <button className="modal-close" onClick={closeModal} aria-label="닫기"><Icon name="close" size={18} /></button>
      </div>
      <div className="modal-body">
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 14, flexWrap: 'wrap' }}>
          <span className={`badge ${STATUS_BADGE[res.status] ?? 'badge-gray'}`}><span className="dot" />{STATUS_LABEL[res.status] ?? res.status}</span>
          {res.url && (
            <span className="repo-box" style={{ flex: 1 }}>
              <span className="u">{res.url}</span>
              <button onClick={() => { navigator.clipboard?.writeText(res.url!); toast('서비스 주소를 복사했습니다.', 'ok') }} title="주소 복사"><Icon name="copy" size={14} /></button>
            </span>
          )}
        </div>

        <div className="side-sec">파이프라인 로그</div>
        <pre className="code" style={{ maxHeight: 200, overflow: 'auto', whiteSpace: 'pre-wrap' }}>{res.log ?? '(로그 없음)'}</pre>

        {res.manifest && (
          <>
            <div className="side-sec" style={{ marginTop: 12 }}>렌더링된 K8s 매니페스트</div>
            <pre className="code" style={{ maxHeight: 280, overflow: 'auto' }}>{res.manifest}</pre>
          </>
        )}
      </div>
      <div className="modal-foot">
        <button className="btn" onClick={closeModal}>닫기</button>
      </div>
    </div>
  )
}
