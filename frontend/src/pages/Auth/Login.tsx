import { useState, type FormEvent } from 'react'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import { AuthShell, AuthField } from './AuthShell'
import { checkUsername, collect, hasError, required, type Errors } from './validation'

type K = 'username' | 'password'

export function Login() {
  const { login, demoPending, signIn, goAuth, toast } = useApp()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Errors<K>>({})
  const [submitting, setSubmitting] = useState(false)

  const validate = (): Errors<K> => collect<K>([
    ['username', checkUsername(username)],
    ['password', required(password, '비밀번호')],
  ])

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const next = validate()
    setErrors(next)
    if (hasError(next)) return

    setSubmitting(true)
    try {
      await signIn(username.trim(), password)
      toast('로그인되었습니다.', 'ok')
    } catch (err) {
      toast((err as Error).message, 'warn')
    } finally {
      setSubmitting(false)
    }
  }

  const clear = (k: K) => setErrors((prev) => ({ ...prev, [k]: undefined }))

  return (
    <AuthShell>
      <h2>로그인</h2>
      <div className="ac-sub">교육청 코드 공유 포털 계정으로 로그인합니다.</div>

      <form className="auth-form" onSubmit={onSubmit} noValidate>
        <AuthField label="아이디" required error={errors.username}>
          <input
            className={`input${errors.username ? ' err' : ''}`}
            value={username}
            autoComplete="username"
            placeholder="아이디를 입력하세요"
            disabled={submitting}
            onChange={(e) => { setUsername(e.target.value); clear('username') }}
            onBlur={() => setErrors((p) => ({ ...p, username: checkUsername(username) ?? undefined }))}
          />
        </AuthField>

        <AuthField label="비밀번호" required error={errors.password}>
          <input
            className={`input${errors.password ? ' err' : ''}`}
            type="password"
            value={password}
            autoComplete="current-password"
            placeholder="비밀번호를 입력하세요"
            disabled={submitting}
            onChange={(e) => { setPassword(e.target.value); clear('password') }}
            onBlur={() => setErrors((p) => ({ ...p, password: required(password, '비밀번호') ?? undefined }))}
          />
        </AuthField>

        <div className="auth-actions">
          <button type="submit" className="btn btn-lg btn-primary" disabled={submitting}>
            {submitting ? '로그인 중...' : '로그인'}
          </button>
        </div>
      </form>

      <div className="auth-links">
        <button type="button" onClick={() => goAuth('signup')}>회원가입</button>
        <span className="sep">|</span>
        <button type="button" onClick={() => goAuth('find-id')}>아이디 찾기</button>
        <span className="sep">|</span>
        <button type="button" onClick={() => goAuth('find-pw')}>비밀번호 찾기</button>
      </div>

      <div className="auth-divider">시연용</div>

      <div className="demo-box">
        <div className="db-head">
          <Icon name="sparkle" size={15} />
          <span className="db-title">데모 로그인</span>
        </div>
        <div className="db-desc">
          계정 입력 없이 바로 둘러봅니다. 진입 후 좌측 하단에서 일반 사용자·바이브 코더·운영 관리자
          권한을 전환해 각 역할 화면을 확인할 수 있습니다.
        </div>
        <button type="button" className="btn btn-lg btn-navy" onClick={login} disabled={demoPending}>
          {demoPending ? '진입 중...' : '데모로 시작하기'}
        </button>
      </div>

      <div className="auth-note">
        아이디 찾기·비밀번호 찾기는 입력값 검증까지 동작하며, 이메일 발송 연동 이후 완성됩니다.
      </div>
    </AuthShell>
  )
}
