import { useState, type FormEvent } from 'react'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import { AuthShell, AuthField, AuthBack, PendingBox } from './AuthShell'
import { checkEmail, checkUsername, collect, hasError, type Errors } from './validation'

type K = 'username' | 'email'

export function FindPassword() {
  const { goAuth, toast } = useApp()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [errors, setErrors] = useState<Errors<K>>({})
  const [sent, setSent] = useState(false)

  const validate = (): Errors<K> => collect<K>([
    ['username', checkUsername(username)],
    ['email', checkEmail(email)],
  ])

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    const next = validate()
    setErrors(next)
    if (hasError(next)) {
      toast('입력값을 다시 확인해 주세요.', 'warn')
      return
    }
    // 2단계에서 auth-service 비밀번호 재설정 API 연동
    setSent(true)
  }

  const clear = (k: K) => setErrors((prev) => ({ ...prev, [k]: undefined }))
  const blur = (k: K, msg: string | null) => setErrors((p) => ({ ...p, [k]: msg ?? undefined }))

  return (
    <AuthShell>
      <AuthBack onClick={() => goAuth('login')} label="로그인으로 돌아가기" />
      <h2>비밀번호 찾기</h2>
      <div className="ac-sub">
        아이디와 가입 시 등록한 이메일을 확인한 뒤 비밀번호 재설정 절차를 안내합니다.
      </div>

      <div className="auth-steps">
        <div className={`auth-step${sent ? '' : ' on'}`}>
          <span className="as-no">1</span>본인 확인
        </div>
        <span className="as-bar" />
        <div className={`auth-step${sent ? ' on' : ''}`}>
          <span className="as-no">2</span>재설정 안내
        </div>
      </div>

      {sent ? (
        <>
          <div className="auth-result">
            <div className="ar-head"><Icon name="check" size={15} />본인 확인 정보 입력 완료</div>
            <div className="ar-body">
              아이디 <b>{username}</b> 계정의 비밀번호 재설정 메일 발송은
              auth-service 및 SMTP 연동 이후 동작합니다.
            </div>
          </div>
          <div className="auth-note">
            연동 완료 후에는 등록된 이메일로 재설정 링크가 발송되며, 링크는 발급 후 일정 시간이
            지나면 만료됩니다. 메일이 도착하지 않으면 스팸함을 확인해 주세요.
          </div>
          <div className="auth-actions">
            <button type="button" className="btn btn-lg btn-primary" onClick={() => goAuth('login')}>
              로그인하기
            </button>
            <button type="button" className="btn btn-lg btn-ghost" onClick={() => setSent(false)}>
              다시 입력하기
            </button>
          </div>
        </>
      ) : (
        <form className="auth-form" onSubmit={onSubmit} noValidate>
          <AuthField label="아이디" required error={errors.username}>
            <input
              className={`input${errors.username ? ' err' : ''}`}
              value={username}
              autoComplete="username"
              placeholder="아이디를 입력하세요"
              onChange={(e) => { setUsername(e.target.value); clear('username') }}
              onBlur={() => blur('username', checkUsername(username))}
            />
          </AuthField>

          <AuthField label="이메일" required error={errors.email}>
            <input
              className={`input${errors.email ? ' err' : ''}`}
              type="email"
              value={email}
              autoComplete="email"
              placeholder="가입 시 등록한 이메일"
              onChange={(e) => { setEmail(e.target.value); clear('email') }}
              onBlur={() => blur('email', checkEmail(email))}
            />
          </AuthField>

          <PendingBox
            title="이메일 인증"
            desc="SMTP 연동 이후 입력한 이메일로 인증번호를 발송합니다. 현재는 입력 자리만 배치되어 있습니다."
          >
            <div className="field">
              <label>인증번호</label>
              <div className="field-row">
                <input className="input" placeholder="6자리 숫자" disabled />
                <button type="button" className="btn" disabled>인증번호 발송</button>
              </div>
            </div>
          </PendingBox>

          <div className="auth-actions">
            <button type="submit" className="btn btn-lg btn-primary">재설정 메일 받기</button>
          </div>
        </form>
      )}

      <div className="auth-links">
        <button type="button" onClick={() => goAuth('login')}>로그인</button>
        <span className="sep">|</span>
        <button type="button" onClick={() => goAuth('signup')}>회원가입</button>
        <span className="sep">|</span>
        <button type="button" onClick={() => goAuth('find-id')}>아이디 찾기</button>
      </div>
    </AuthShell>
  )
}
