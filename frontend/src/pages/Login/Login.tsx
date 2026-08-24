import './Login.css'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'

const POINTS = [
  '교육에서 만든 프로그램을 GitHub 레포로 올리면 새 서비스로 배포됩니다.',
  '업무 분야·기능 유형·기술로 원하는 프로그램을 빠르게 찾습니다.',
  '설치 없이 웹에서 바로 사용하거나 소스코드를 내려받습니다.',
]

export function Login() {
  const { login, toast } = useApp()
  const notReady = () => toast('아직 구현되지 않은 기능입니다. 데모로 진입해 주세요.', 'warn')

  return (
    <div className="login-shell">
      <div className="login-left">
        <div className="lg-brand">
          <div className="brand-mark">EC</div>
          <div>
            <div className="bn">교육청 코드 공유</div>
            <div className="bs">내부 업무 프로그램 공유 포털</div>
          </div>
        </div>
        <h1>업무를 돕는 프로그램을<br /><span className="hl">한 곳에서 찾고, 바로 사용</span>하세요.</h1>
        <p>교육청 구성원이 만든 업무 자동화·생성·분석 프로그램을 공유하고, 표준 규격에 맞춰
          올리면 하나의 서비스로 배포되는 내부 플랫폼입니다.</p>
        <div className="lg-points">
          {POINTS.map((t) => (
            <div key={t} className="lg-point"><span className="lp-ico"><Icon name="check" size={16} /></span>{t}</div>
          ))}
        </div>
      </div>

      <div className="login-right">
        <div className="login-card">
          <span className="login-demo-flag"><Icon name="info" size={13} /> DEMO · 인증 미구현</span>
          <h2>로그인</h2>
          <div className="lc-sub">아직 인증 기능은 준비 중입니다. 아래 버튼으로 데모에 진입하세요.</div>

          <div className="field">
            <label>아이디</label>
            <input className="input" placeholder="demo" defaultValue="demo" />
          </div>
          <div className="field">
            <label>비밀번호</label>
            <input className="input" type="password" placeholder="비밀번호" defaultValue="demo1234" />
          </div>

          <div className="login-actions">
            <button className="btn btn-lg btn-primary" onClick={login}>데모로 시작하기</button>
            <button className="btn btn-lg" onClick={notReady}>로그인</button>
          </div>

          <div className="login-links">
            <button onClick={notReady}>회원가입</button>
            <span className="sep">|</span>
            <button onClick={notReady}>아이디 찾기</button>
            <span className="sep">|</span>
            <button onClick={notReady}>비밀번호 찾기</button>
          </div>

          <div className="login-note">
            시연용 데모입니다. 로그인/로그아웃/회원가입/아이디·비밀번호 찾기 화면은 존재하지만
            아직 동작하지 않습니다. "데모로 시작하기"로 진입한 뒤, 좌측 하단에서 권한을 전환해
            각 역할 화면을 확인할 수 있습니다.
          </div>
        </div>
      </div>
    </div>
  )
}
