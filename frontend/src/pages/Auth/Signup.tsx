import { useState, type FormEvent } from 'react'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import { authApi, type DuplicateField } from '../../api/auth'
import { AuthShell, AuthField, AuthBack, PendingBox } from './AuthShell'
import {
  checkEmail, checkPassword, checkPasswordConfirm, checkUsername, collect, hasError,
  passwordRules, required, type Errors,
} from './validation'

type K = 'username' | 'password' | 'passwordConfirm' | 'name' | 'dept' | 'email'

export function Signup() {
  const { goAuth, toast } = useApp()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [name, setName] = useState('')
  const [dept, setDept] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [requestRole, setRequestRole] = useState<'' | 'coder' | 'admin'>('')
  const [requestReason, setRequestReason] = useState('')
  const [errors, setErrors] = useState<Errors<K>>({})
  const [submitting, setSubmitting] = useState(false)
  const [checking, setChecking] = useState<DuplicateField | null>(null)

  const rules = passwordRules(password)
  const usernameValid = checkUsername(username) === null
  const emailValid = checkEmail(email) === null

  const validate = (): Errors<K> => collect<K>([
    ['username', checkUsername(username)],
    ['password', checkPassword(password)],
    ['passwordConfirm', checkPasswordConfirm(passwordConfirm, password)],
    ['name', required(name, '이름')],
    ['dept', required(dept, '부서')],
    ['email', checkEmail(email)],
  ])

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const next = validate()
    setErrors(next)
    if (hasError(next)) {
      toast('입력값을 다시 확인해 주세요.', 'warn')
      return
    }

    setSubmitting(true)
    try {
      // 계정은 항상 USER 로 저장된다. 상향 권한은 신청으로만 보관되고 운영 관리자 승인 시 적용된다.
      await authApi.signup({
        username: username.trim(),
        password,
        name: name.trim(),
        email: email.trim(),
        dept: dept.trim(),
        ...(requestRole ? { requestRole, requestReason: requestReason.trim() } : {}),
      })
      toast(
        requestRole
          ? '회원가입이 완료되었습니다. 신청한 권한은 운영 관리자 승인 후 적용됩니다. 우선 일반 사용자로 로그인해 주세요.'
          : '회원가입이 완료되었습니다. 가입한 아이디로 로그인해 주세요.',
        'ok',
      )
      goAuth('login')
    } catch (err) {
      toast((err as Error).message, 'warn')
    } finally {
      setSubmitting(false)
    }
  }

  const clear = (k: K) => setErrors((prev) => ({ ...prev, [k]: undefined }))
  const blur = (k: K, msg: string | null) => setErrors((p) => ({ ...p, [k]: msg ?? undefined }))

  const checkDuplicate = async (field: DuplicateField) => {
    const label = field === 'username' ? '아이디' : '이메일'
    const value = (field === 'username' ? username : email).trim()

    setChecking(field)
    try {
      const res = await authApi.checkDuplicate(field, value)
      if (res.available) {
        toast(`사용할 수 있는 ${label}입니다.`, 'ok')
      } else {
        toast(`이미 사용 중인 ${label}입니다.`, 'warn')
        setErrors((p) => ({ ...p, [field]: `이미 사용 중인 ${label}입니다.` }))
      }
    } catch (err) {
      toast((err as Error).message, 'warn')
    } finally {
      setChecking(null)
    }
  }

  return (
    <AuthShell wide>
      <AuthBack onClick={() => goAuth('login')} label="로그인으로 돌아가기" />
      <h2>회원가입</h2>
      <div className="ac-sub">
        가입 시 기본 권한은 일반 사용자입니다. 바이브 코더·운영 관리자 권한이 필요하면
        아래에서 신청할 수 있으며, 운영 관리자 승인 후 적용됩니다.
      </div>

      <form className="auth-form" onSubmit={onSubmit} noValidate>
        <AuthField
          label="아이디"
          required
          error={errors.username}
          hint="영문 소문자로 시작하는 4~20자 (영문 소문자·숫자·밑줄)"
        >
          <div className="field-row">
            <input
              className={`input${errors.username ? ' err' : ''}`}
              value={username}
              autoComplete="username"
              placeholder="예: hongildong"
              onChange={(e) => { setUsername(e.target.value); clear('username') }}
              onBlur={() => blur('username', checkUsername(username))}
            />
            <button
              type="button"
              className="btn"
              disabled={!usernameValid || checking !== null}
              onClick={() => checkDuplicate('username')}
            >
              {checking === 'username' ? '확인 중' : '중복 확인'}
            </button>
          </div>
        </AuthField>

        <AuthField label="비밀번호" required error={errors.password}>
          <input
            className={`input${errors.password ? ' err' : ''}`}
            type="password"
            value={password}
            autoComplete="new-password"
            placeholder="비밀번호를 입력하세요"
            onChange={(e) => { setPassword(e.target.value); clear('password') }}
            onBlur={() => blur('password', checkPassword(password))}
          />
          <div className="pw-rules">
            {rules.map((r) => (
              <div key={r.key} className={`pw-rule${r.ok ? ' ok' : ''}`}>
                <span className="pr-ico"><Icon name={r.ok ? 'check' : 'close'} size={12} /></span>
                {r.label}
              </div>
            ))}
          </div>
        </AuthField>

        <AuthField label="비밀번호 확인" required error={errors.passwordConfirm}>
          <input
            className={`input${errors.passwordConfirm ? ' err' : ''}`}
            type="password"
            value={passwordConfirm}
            autoComplete="new-password"
            placeholder="비밀번호를 다시 입력하세요"
            onChange={(e) => { setPasswordConfirm(e.target.value); clear('passwordConfirm') }}
            onBlur={() => blur('passwordConfirm', checkPasswordConfirm(passwordConfirm, password))}
          />
        </AuthField>

        <div className="grid-2">
          <AuthField label="이름" required error={errors.name}>
            <input
              className={`input${errors.name ? ' err' : ''}`}
              value={name}
              autoComplete="name"
              placeholder="예: 홍길동"
              onChange={(e) => { setName(e.target.value); clear('name') }}
              onBlur={() => blur('name', required(name, '이름'))}
            />
          </AuthField>

          <AuthField label="부서" required error={errors.dept}>
            <input
              className={`input${errors.dept ? ' err' : ''}`}
              value={dept}
              placeholder="예: 교육과정과"
              onChange={(e) => { setDept(e.target.value); clear('dept') }}
              onBlur={() => blur('dept', required(dept, '부서'))}
            />
          </AuthField>
        </div>

        <AuthField label="이메일" required error={errors.email}>
          <div className="field-row">
            <input
              className={`input${errors.email ? ' err' : ''}`}
              type="email"
              value={email}
              autoComplete="email"
              placeholder="예: hongildong@edu.local"
              onChange={(e) => { setEmail(e.target.value); clear('email') }}
              onBlur={() => blur('email', checkEmail(email))}
            />
            <button
              type="button"
              className="btn"
              disabled={!emailValid || checking !== null}
              onClick={() => checkDuplicate('email')}
            >
              {checking === 'email' ? '확인 중' : '중복 확인'}
            </button>
          </div>
        </AuthField>

        <AuthField
          label="권한 신청 (선택)"
          hint="상향 권한은 운영 관리자 승인 후 적용됩니다. 미선택 시 일반 사용자로 가입합니다."
        >
          <select
            className="input"
            value={requestRole}
            onChange={(e) => setRequestRole(e.target.value as '' | 'coder' | 'admin')}
          >
            <option value="">일반 사용자 (기본)</option>
            <option value="coder">바이브 코더 — 프로그램 등록·배포</option>
            <option value="admin">운영 관리자 — 검토·권한·배포 관리</option>
          </select>
        </AuthField>

        {requestRole && (
          <AuthField label="신청 사유" hint="승인 검토에 참고됩니다. (최대 300자)">
            <textarea
              className="input"
              rows={2}
              maxLength={300}
              value={requestReason}
              placeholder="예: 학사 업무 자동화 프로그램을 등록·운영하기 위해 코더 권한이 필요합니다."
              onChange={(e) => setRequestReason(e.target.value)}
            />
          </AuthField>
        )}

        <PendingBox
          title="본인 확인"
          desc="이메일·휴대폰 인증번호 발송은 SMTP·SMS 연동 이후 활성화됩니다. 현재는 입력 자리만 배치되어 있습니다."
        >
          <div className="field">
            <label>휴대폰 번호</label>
            <div className="field-row">
              <input
                className="input"
                value={phone}
                autoComplete="tel"
                placeholder="010-1234-5678"
                onChange={(e) => setPhone(e.target.value)}
              />
              <button type="button" className="btn" disabled>인증번호 발송</button>
            </div>
          </div>
          <div className="field">
            <label>인증번호</label>
            <div className="field-row">
              <input className="input" placeholder="6자리 숫자" disabled />
              <button type="button" className="btn" disabled>확인</button>
            </div>
          </div>
        </PendingBox>

        <div className="auth-actions">
          <button type="submit" className="btn btn-lg btn-primary" disabled={submitting}>
            {submitting ? '가입 처리 중...' : '가입하기'}
          </button>
          <button type="button" className="btn btn-lg" disabled={submitting}
            onClick={() => goAuth('login')}>취소</button>
        </div>
      </form>
    </AuthShell>
  )
}
