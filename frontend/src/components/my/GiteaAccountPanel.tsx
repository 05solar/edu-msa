import { useEffect, useState } from 'react'
import { api, USE_API, type GiteaAccountStatus } from '../../api/client'
import { Icon } from '../../icons/Icon'

/**
 * 마이페이지 · Gitea 계정 셀프 발급 패널.
 * 서버에 Gitea 연동이 구성되지 않았으면(enabled=false) 아무것도 그리지 않는다.
 * 링크는 프로토콜 상대(//host) — 플랫폼과 같은 스킴(kind http / 실서버 https)으로 열린다.
 */
export function GiteaAccountPanel() {
  const [status, setStatus] = useState<GiteaAccountStatus | null>(null)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [created, setCreated] = useState(false)

  useEffect(() => {
    if (!USE_API) return
    api.giteaAccount().then(setStatus).catch(() => { /* 미로그인·서버 오류 시 패널 숨김 */ })
  }, [])

  if (!status?.enabled) return null

  const idOk = /^[a-zA-Z][a-zA-Z0-9._-]{2,38}$/.test(username)
  const pwOk = password.length >= 8
  const matchOk = password === confirm

  const submit = async () => {
    setError('')
    setSubmitting(true)
    try {
      const res = await api.createGiteaAccount(username.trim(), password)
      setStatus(res)
      setCreated(true)
      setPassword(''); setConfirm('')
    } catch (e) {
      setError(e instanceof Error ? e.message : '발급에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="panel" style={{ marginBottom: 16 }}>
      <div className="panel-head"><div className="panel-title">Gitea 계정 (내부 코드 저장소)</div></div>
      <div className="panel-body">
        {status.issued ? (
          <div>
            <div style={{ marginBottom: 6 }}>
              발급된 아이디: <b>{status.username}</b>
              {created && <span style={{ color: 'var(--ok, #17693a)', marginLeft: 8, fontSize: 13 }}>✓ 발급 완료</span>}
            </div>
            <div style={{ fontSize: 13, color: 'var(--ink-400)', marginBottom: 10 }}>
              프로그램 소스는 내부 Gitea 저장소에 올려 등록합니다. 비밀번호 변경은 Gitea 설정에서 직접 합니다.
            </div>
            <a className="btn btn-sm btn-primary" href={`//${status.host}`} target="_blank" rel="noreferrer">
              <Icon name="upload" size={13} /> Gitea 열기
            </a>
          </div>
        ) : (
          <div>
            <div style={{ fontSize: 13, color: 'var(--ink-400)', marginBottom: 10 }}>
              내부 코드 저장소(Gitea) 계정을 발급합니다. 아이디는 <b>영문으로 시작하는 3~39자의 영문/숫자/._-</b>,
              비밀번호는 <b>8자 이상</b>입니다. 계정은 1인 1개만 발급됩니다.
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', maxWidth: 640 }}>
              <input
                className="input" style={{ width: 180 }} value={username} maxLength={39}
                placeholder="영문 아이디 (예: peter)" autoComplete="off"
                onChange={(e) => setUsername(e.target.value)}
              />
              <input
                className="input" style={{ width: 180 }} type="password" value={password}
                placeholder="비밀번호 (8자 이상)" autoComplete="new-password"
                onChange={(e) => setPassword(e.target.value)}
              />
              <input
                className="input" style={{ width: 180 }} type="password" value={confirm}
                placeholder="비밀번호 확인" autoComplete="new-password"
                onChange={(e) => setConfirm(e.target.value)}
              />
              <button
                className="btn btn-sm btn-primary"
                disabled={submitting || !idOk || !pwOk || !matchOk}
                onClick={submit}
              >{submitting ? '발급 중…' : '계정 발급'}</button>
            </div>
            {username && !idOk && (
              <div style={{ color: 'var(--danger, #9c2121)', fontSize: 12, marginTop: 6 }}>
                아이디는 영문으로 시작하는 3~39자의 영문/숫자/._- 만 사용할 수 있습니다.
              </div>
            )}
            {password && !pwOk && (
              <div style={{ color: 'var(--danger, #9c2121)', fontSize: 12, marginTop: 6 }}>비밀번호는 8자 이상이어야 합니다.</div>
            )}
            {confirm && !matchOk && (
              <div style={{ color: 'var(--danger, #9c2121)', fontSize: 12, marginTop: 6 }}>비밀번호가 일치하지 않습니다.</div>
            )}
            {error && (
              <div style={{ color: 'var(--danger, #9c2121)', fontSize: 12, marginTop: 6 }}>{error}</div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
