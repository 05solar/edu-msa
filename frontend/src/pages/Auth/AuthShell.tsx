import type { ReactNode } from 'react'
import './Auth.css'
import { Icon } from '../../icons/Icon'

const POINTS = [
  '교육청에서 만든 프로그램을 GitHub 레포로 올리면 새 서비스로 배포됩니다.',
  '업무 분야·기능 유형·기술로 원하는 프로그램을 빠르게 찾습니다.',
  '설치 없이 웹에서 바로 사용하거나 소스코드를 내려받습니다.',
]

/* 인증 화면 공통 골격 — 좌측 소개 패널 + 우측 카드 */
export function AuthShell({ children, wide }: { children: ReactNode; wide?: boolean }) {
  return (
    <div className="auth-shell">
      <div className="auth-left">
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

        {/* 이용 가이드 — 로그인 전에도 열람 가능하도록 정적 문서로 제공 */}
        <div className="lg-guides">
          <div className="lg-guides-title">처음이신가요? 이용 안내서를 먼저 읽어 보세요.</div>
          <div className="lg-guides-row">
            <a className="lg-guide" href="/guides/guide-staff.html" target="_blank" rel="noreferrer">
              <span className="lp-ico"><Icon name="manual" size={16} /></span>
              직원용 사용 안내
              <span className="lg-ext"><Icon name="external" size={12} /></span>
            </a>
            <a className="lg-guide" href="/guides/guide-admin.html" target="_blank" rel="noreferrer">
              <span className="lp-ico"><Icon name="admin" size={16} /></span>
              관리자용 운영 안내
              <span className="lg-ext"><Icon name="external" size={12} /></span>
            </a>
          </div>
        </div>
      </div>

      <div className={`auth-right${wide ? ' wide' : ''}`}>
        <div className="auth-card">{children}</div>
      </div>
    </div>
  )
}

/* 라벨 + 입력 + 오류 메시지 한 벌 */
export function AuthField({
  label, required, hint, error, children,
}: {
  label: string
  required?: boolean
  hint?: string
  error?: string
  children: ReactNode
}) {
  return (
    <div className="field">
      <label>{label}{required ? <span className="req">*</span> : null}</label>
      {children}
      {error
        ? <div className="field-err"><span className="fe-ico"><Icon name="warn" size={13} /></span>{error}</div>
        : hint ? <div className="hint">{hint}</div> : null}
    </div>
  )
}

/* 추후 연동 예정 영역 (이메일 / 휴대폰 인증번호) — 자리만 배치한다. */
export function PendingBox({
  title, desc, children,
}: {
  title: string
  desc: string
  children: ReactNode
}) {
  return (
    <div className="auth-pending">
      <div className="ap-head">
        <Icon name="info" size={14} />
        {title}
        <span className="badge badge-warn">추후 연동</span>
      </div>
      <div className="ap-desc">{desc}</div>
      {children}
    </div>
  )
}

/* 화면 상단 뒤로가기 */
export function AuthBack({ onClick, label }: { onClick: () => void; label: string }) {
  return (
    <button type="button" className="auth-back" onClick={onClick}>
      <Icon name="chevron-left" size={14} />{label}
    </button>
  )
}
