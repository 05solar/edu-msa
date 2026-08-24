import './GuideModal.css'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'

/*
 * 비전공자용 프로그램 등록 가이드. 코드를 잘 몰라도 순서대로 따라 하면
 * 내가 만든 프로그램을 플랫폼 서비스로 올릴 수 있도록 쉬운 말로 설명한다.
 */
export function GuideModal() {
  const { closeModal } = useApp()

  return (
    <div className="modal wide">
      <div className="modal-head">
        <div>
          <h3>프로그램 등록 가이드</h3>
          <div className="mh-sub">처음이신가요? 아래 순서만 따라 하면 됩니다.</div>
        </div>
        <button className="modal-close" onClick={closeModal} aria-label="닫기"><Icon name="close" size={18} /></button>
      </div>

      <div className="modal-body guide-doc">
        <p className="g-lead">
          내가 만든 프로그램을 이곳에 올리면, 다른 직원들이 <b>설치 없이 웹에서 바로</b> 사용할 수 있어요.
          코딩을 깊게 몰라도 괜찮습니다. 아래 순서대로 따라 하면 됩니다. 어려운 부분은 사용하신
          <b> 바이브 코딩 도구(AI)</b>에게 그대로 부탁하면 대신 만들어 줍니다.
        </p>

        <h4><span className="g-badge">?</span>먼저, 이게 어떻게 동작하나요</h4>
        <p>
          여러분이 코드를 인터넷 저장소(GitHub)에 올려두면, 이 플랫폼이 그 코드를 <b>자동으로 가져와
          포장(컨테이너)해서 하나의 서비스로 띄워 줍니다.</b> 사람이 일일이 설치하지 않아요.
          그래서 "이 프로그램은 이런 것이고, 이렇게 실행한다"는 안내 파일 2개만 규칙에 맞게 넣어 주면 됩니다.
        </p>

        <h4><span className="g-badge">1</span>딱 3가지만 준비하면 돼요</h4>
        <div className="g-prep">
          <div className="g-card">
            <span className="g-ic"><Icon name="gitea" size={18} /></span>
            <div>
              <div className="g-t">GitHub 저장소(레포)</div>
              <div className="g-d">내 코드를 담아두는 인터넷 폴더입니다. 무료로 만들 수 있고, <b>공개(Public)</b>로 두면 됩니다.</div>
            </div>
          </div>
          <div className="g-card">
            <span className="g-ic"><Icon name="file" size={18} /></span>
            <div>
              <div className="g-t">service.yaml — 설명서 파일</div>
              <div className="g-d">"이 프로그램은 무슨 업무를 돕고, 어떤 분야이며, 몇 번 포트로 열린다"를 적는 짧은 파일입니다.</div>
            </div>
          </div>
          <div className="g-card">
            <span className="g-ic"><Icon name="installer" size={18} /></span>
            <div>
              <div className="g-t">Dockerfile — 실행법 파일</div>
              <div className="g-d">"이 프로그램을 이렇게 실행하라"를 적는 파일입니다. 언어(파이썬·Node 등)와 상관없이 이 파일만 있으면 실행돼요.</div>
            </div>
          </div>
        </div>

        <h4><span className="g-badge">2</span>5단계로 끝내기</h4>
        <div className="g-steps">
          <div className="g-step"><span className="g-no">1</span><div className="g-body">
            <div className="g-st">GitHub에 공개 저장소를 만든다</div>
            <div className="g-sd">github.com에 로그인 → New repository → 이름 입력 → <b>Public</b> 선택 → Create.</div>
          </div></div>
          <div className="g-step"><span className="g-no">2</span><div className="g-body">
            <div className="g-st">내가 만든 코드를 그 저장소에 올린다</div>
            <div className="g-sd">파일을 끌어다 놓거나(웹 업로드), 바이브 코딩 도구가 만들어 준 파일들을 그대로 올립니다.</div>
          </div></div>
          <div className="g-step"><span className="g-no">3</span><div className="g-body">
            <div className="g-st">저장소 맨 위(루트)에 <code>service.yaml</code>을 추가한다</div>
            <div className="g-sd">아래 예시를 복사해 내 프로그램에 맞게 이름·분야·포트만 바꾸면 됩니다.</div>
          </div></div>
          <div className="g-step"><span className="g-no">4</span><div className="g-body">
            <div className="g-st">저장소 맨 위에 <code>Dockerfile</code>을 추가한다</div>
            <div className="g-sd">직접 쓰기 어렵다면, 아래 "AI에게 이렇게 말하세요"를 그대로 부탁하세요.</div>
          </div></div>
          <div className="g-step"><span className="g-no">5</span><div className="g-body">
            <div className="g-st">이 등록 화면에 저장소 주소를 붙여넣는다</div>
            <div className="g-sd"><b>레포 규격 검증</b>을 눌러 통과를 확인한 뒤 <b>등록 요청</b>을 누르면, 운영 관리자 승인 후 서비스로 배포됩니다.</div>
          </div></div>
        </div>

        <h4><span className="g-badge">3</span>service.yaml 이렇게 쓰면 돼요</h4>
        <div className="code">{`name: 출장 정산 자동 계산기
slug: travel-settlement
category: budget
purposes: [auto, gen]
tech: [Python]
summary: 출장 내역을 넣으면 정산액을 자동 계산합니다.
port: 8080
health: /healthz`}</div>
        <table className="g-table">
          <thead><tr><th>항목</th><th>뜻</th><th>예시</th></tr></thead>
          <tbody>
            <tr><td><code>name</code></td><td>화면에 보일 프로그램 이름 (한글 가능)</td><td>출장 정산 자동 계산기</td></tr>
            <tr><td><code>slug</code></td><td>영문 소문자·하이픈으로 된 식별용 이름</td><td>travel-settlement</td></tr>
            <tr><td><code>category</code></td><td>업무 분야</td><td>doc / student / curri / budget / facil / data / civil 중 하나</td></tr>
            <tr><td><code>port</code></td><td>프로그램이 열리는 포트 번호</td><td>8080</td></tr>
            <tr><td><code>health</code></td><td>살아있는지 확인하는 주소 (기본 <code>/healthz</code>)</td><td>/healthz</td></tr>
          </tbody>
        </table>

        <h4><span className="g-badge">4</span>꼭 지킬 2가지 약속</h4>
        <p>이 두 가지만 지키면 플랫폼이 프로그램을 안정적으로 띄울 수 있어요.</p>
        <div className="g-callout">
          <span className="g-ci"><Icon name="info" size={16} /></span>
          <span><b>① 포트는 고정하지 말고 PORT를 읽게 하세요.</b> 프로그램이 열리는 포트를 코드에
            <code> 8501</code>처럼 박아두지 말고, 환경변수 <code>PORT</code> 값을 사용하도록 합니다.
            (AI에게 "PORT 환경변수로 열리게 해줘"라고 부탁하면 됩니다.)</span>
        </div>
        <div className="g-callout">
          <span className="g-ci"><Icon name="info" size={16} /></span>
          <span><b>② 건강 확인 주소를 하나 만드세요.</b> <code>GET /healthz</code>로 접속하면 그냥
            <code> ok</code>라고 응답하는 아주 짧은 주소면 됩니다. 플랫폼이 이걸로 "잘 떠 있는지"를 확인합니다.</span>
        </div>

        <h4><span className="g-badge">5</span>AI(바이브 코딩 도구)에게 이렇게 말하세요</h4>
        <p>파일 만들기가 어렵다면, 사용하는 AI 도구에 아래 문장을 그대로 붙여넣어 부탁하세요.</p>
        <div className="g-prompt">
          "내 프로젝트를 교육청 플랫폼에 올리려고 해. 레포 루트에 <b>service.yaml</b>과 <b>Dockerfile</b>을
          만들어 줘. 앱은 <b>환경변수 PORT</b> 포트로 열리게 하고, <b>GET /healthz</b>에서 200으로 'ok'를
          응답하게 해 줘. service.yaml에는 name, slug, category, port, health를 넣어 줘."
        </div>

        <h4><span className="g-badge">6</span>자주 하는 실수</h4>
        <ul className="g-list x">
          <li>포트를 코드에 숫자로 박아둠 → 환경변수 <code>PORT</code>를 읽도록 바꾸기</li>
          <li><code>service.yaml</code>을 하위 폴더에 둠 → 반드시 저장소 <b>맨 위(루트)</b>에 두기</li>
          <li><code>/healthz</code> 주소가 없음 → 짧게 하나 추가하기</li>
          <li>저장소를 비공개로 둠 → <b>공개(Public)</b>로 바꾸기</li>
          <li>개인정보·비밀번호를 코드나 데이터에 넣음 → 절대 올리지 않기</li>
        </ul>

        <h4><span className="g-badge">7</span>올리기 전에 확인</h4>
        <ul className="g-list">
          <li>공개 GitHub 저장소가 있고 주소를 안다</li>
          <li>루트에 <code>service.yaml</code>이 있고 이름·분야·포트가 채워져 있다</li>
          <li>루트에 <code>Dockerfile</code>이 있다</li>
          <li>프로그램이 <code>PORT</code>로 열리고 <code>/healthz</code>가 응답한다</li>
          <li>민감한 정보가 저장소에 없다</li>
        </ul>

        <div className="g-callout warn">
          <span className="g-ci"><Icon name="warn" size={16} /></span>
          <span>시험 삼아 해보고 싶다면, 위 등록 화면의 레포 주소 칸에
            <code> sample://travel-settlement</code>을 넣고 <b>레포 규격 검증</b>을 눌러 보세요.
            실제 저장소 없이도 검증 과정을 미리 볼 수 있습니다.</span>
        </div>
      </div>

      <div className="modal-foot">
        <button className="btn btn-primary" onClick={closeModal}>이해했어요, 등록하기</button>
      </div>
    </div>
  )
}
