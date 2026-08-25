import { useState, type FormEvent } from 'react'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import { AuthShell, AuthField, AuthBack, PendingBox } from './AuthShell'
import { checkEmail, collect, hasError, required, type Errors } from './validation'

type K = 'name' | 'email'

export function FindId() {
  const { goAuth, toast } = useApp()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [errors, setErrors] = useState<Errors<K>>({})
  const [done, setDone] = useState(false)

  const validate = (): Errors<K> => collect<K>([
    ['name', required(name, '이름')],
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
    // 2단계에서 auth-service 조회 API 연동
    setDone(true)
  }

  const clear = (k: K) => setErrors((prev) => ({ ...prev, [k]: undefined }))
  const blur = (k: K, msg: string | null) => setErrors((p) => ({ ...p, [k]: msg ?? undefined }))

  return (
    <AuthShell>
      <AuthBack onClick={() => goAuth('login')} label="로그인으로 돌아가기" />
      <h2>아이디 찾기</h2>
      <div className="ac-sub">가입 시 등록한 이름과 이메일로 아이디를 조회합니다.</div>

      <div className="auth-steps">
        <div className={`auth-step${done ? '' : ' on'}`}>
          <span className="as-no">1</span>본인 확인
        </div>
        <span className="as-bar" />
        <div className={`auth-step${done ? ' on' : ''}`}>
          <span className="as-no">2</span>결과 확인
        </div>
      </div>

      {done ? (
        <>
          <div className="auth-result">
            <div className="ar-head"><Icon name="check" size={15} />본인 확인 정보 입력 완료</div>
            <div className="ar-body">
              {name} 님의 아이디는 auth-service 연동 후 이 영역에 표시됩니다.
              조회 결과는 개인정보 보호를 위해 일부가 가려진 형태로 제공됩니다.
            </div>
          </div>
          <div className="auth-actions">
            <button type="button" className="btn btn-lg btn-primary" onClick={() => goAuth('login')}>
              로그인하기
            </button>
            <button type="button" className="btn btn-lg" onClick={() => goAuth('find-pw')}>
              비밀번호 찾기
            </button>
            <button type="button" className="btn btn-lg btn-ghost" onClick={() => setDone(false)}>
              다시 조회하기
            </button>
          </div>
        </>
      ) : (
        <form className="auth-form" onSubmit={onSubmit} noValidate>
          <AuthField label="이름" required error={errors.name}>
            <input
              className={`input${errors.name ? ' err' : ''}`}
              value={name}
              autoComplete="name"
              placeholder="가입 시 등록한 이름"
              onChange={(e) => { setName(e.target.value); clear('name') }}
              onBlur={() => blur('name', required(name, '이름'))}
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
            <button type="submit" className="btn btn-lg btn-primary">아이디 찾기</button>
          </div>
        </form>
      )}

      <div className="auth-links">
        <button type="button" onClick={() => goAuth('login')}>로그인</button>
        <span className="sep">|</span>
        <button type="button" onClick={() => goAuth('signup')}>회원가입</button>
        <span className="sep">|</span>
        <button type="button" onClick={() => goAuth('find-pw')}>비밀번호 찾기</button>
      </div>
    </AuthShell>
  )
}
