import './GuideModal.css'
import { useEffect, useState } from 'react'
import { useApp } from '../../state/AppContext'
import { Icon } from '../../icons/Icon'
import { api, USE_API } from '../../api/client'

/*
 * 프로그램 등록 가이드 — 개발 경험이 없는 비전공자도 순서대로 따라 하면
 * 자신이 만든 프로그램을 플랫폼 서비스로 올릴 수 있도록 실제 용어를 쉬운 말로
 * 단계별로 자세히 설명한다.
 * 코드는 외부(GitHub)가 아니라 내부 Gitea 저장소에만 올린다(내부망 정책).
 */
export function GuideModal() {
  const { closeModal } = useApp()
  // 내부 Gitea 실제 호스트 — 서버 설정에서 받아 안내문에 표시(미연동 시 예시 주소로 대체)
  const [giteaHost, setGiteaHost] = useState<string | null>(null)
  useEffect(() => {
    if (!USE_API) return
    api.giteaAccount().then((s) => { if (s.host) setGiteaHost(s.host) }).catch(() => { /* 안내문은 예시 주소 사용 */ })
  }, [])
  const host = giteaHost ?? 'gitea.<플랫폼 주소>'

  return (
    <div className="modal wide">
      <div className="modal-head">
        <div>
          <h3>프로그램 등록 가이드</h3>
          <div className="mh-sub">개발이 처음이어도 괜찮습니다. 순서대로 따라오세요.</div>
        </div>
        <button className="modal-close" onClick={closeModal} aria-label="닫기"><Icon name="close" size={18} /></button>
      </div>

      <div className="modal-body guide-doc">
        <p className="g-lead">
          내가 만든 프로그램을 이곳에 등록하면, 플랫폼이 그 코드를 자동으로 가져와 실행해 주고
          다른 직원들이 <b>설치 없이 웹에서 바로</b> 사용할 수 있습니다. 아래 순서를 그대로 따라 하면
          되고, 코드를 직접 작성하기 어려운 부분은 사용하시는 <b>AI 코딩 도구</b>에 그대로 부탁하면 됩니다.
        </p>

        <h4><span className="g-badge">↓</span>지시서 내려받기 (AI에게 첨부하세요)</h4>
        <p><b>AI 빌드 지시서 하나만</b> AI 코딩 도구(Claude·Cursor·Copilot 등)에 첨부하고
          <b> "이 문서 규격대로 내 프로그램 프로젝트를 만들어 줘"</b>라고 하면, 그대로 배포되는
          프로젝트가 만들어집니다. (이 파일 안에 스택별 완성 템플릿까지 모두 들어 있습니다.
          아래 개별 스택 템플릿은 선택용 빠른 참고입니다.)</p>
        <div className="g-downloads">
          <a className="btn btn-navy" href="/guides/AI_BUILD_SPEC.md" download>
            <Icon name="download" size={15} /> AI 빌드 지시서 (필수)
          </a>
          <a className="btn" href="/guides/template-python.md" download><Icon name="download" size={14} /> 파이썬 템플릿</a>
          <a className="btn" href="/guides/template-node.md" download><Icon name="download" size={14} /> Node.js 템플릿</a>
          <a className="btn" href="/guides/template-static.md" download><Icon name="download" size={14} /> 정적 웹 템플릿</a>
        </div>

        <h4><span className="g-badge">i</span>먼저 알아둘 용어 3가지</h4>
        <p>딱 세 가지 개념만 알면 됩니다. 지금 다 이해 못 해도 괜찮고, 아래 단계에서 다시 설명합니다.</p>
        <div className="g-prep">
          <div className="g-card">
            <span className="g-ic"><Icon name="gitea" size={18} /></span>
            <div>
              <div className="g-t">내부 Gitea · 저장소(레포지토리)</div>
              <div className="g-d">코드를 보관하고 공유하는 <b>교육청 내부 서비스</b>가 <b>Gitea</b>이고, 그 안에서 프로그램
                하나를 담는 폴더가 <b>저장소(repository)</b>입니다. 여기에 코드를 올려 두면 플랫폼이 그 주소로
                코드를 가져갑니다. <b>외부 서비스(GitHub 등)는 사용하지 않습니다</b> — 코드가 기관 밖으로 나가지 않습니다.</div>
            </div>
          </div>
          <div className="g-card">
            <span className="g-ic"><Icon name="file" size={18} /></span>
            <div>
              <div className="g-t">service.yaml · 설정 파일</div>
              <div className="g-d">프로그램의 이름·분야·포트 번호 등 <b>기본 정보</b>를 적어 두는 짧은 텍스트 파일입니다.
                플랫폼이 이 파일을 읽어 프로그램을 어떻게 등록할지 판단합니다.</div>
            </div>
          </div>
          <div className="g-card">
            <span className="g-ic"><Icon name="installer" size={18} /></span>
            <div>
              <div className="g-t">Dockerfile · 실행 방법 파일</div>
              <div className="g-d"><b>프로그램을 실행하는 방법</b>을 적어 두는 파일입니다. 이 파일 덕분에 파이썬·Node 등
                어떤 언어로 만들었든 플랫폼이 동일한 방식으로 실행할 수 있습니다.</div>
            </div>
          </div>
        </div>

        <h4><span className="g-badge">1</span>1단계 · 내부 Gitea에 코드 올리기</h4>
        <div className="g-steps">
          <div className="g-step"><span className="g-no">1</span><div className="g-body">
            <div className="g-st">Gitea 계정 발급받기 (이미 있으면 넘어가기)</div>
            <div className="g-sd"><b>마이페이지 → Gitea 계정</b> 패널에서 영문 아이디와 비밀번호(8자 이상)를
              정해 <b>계정 발급</b>을 누릅니다. 발급 후 같은 패널의 <b>Gitea 열기</b> 버튼으로
              <code> {host}</code>에 접속해 로그인합니다.</div>
          </div></div>
          <div className="g-step"><span className="g-no">2</span><div className="g-body">
            <div className="g-st">새 저장소 만들기</div>
            <div className="g-sd">오른쪽 위 <b>+</b> → <b>새 저장소(New Repository)</b> → 저장소 이름(영문) 입력 →
              <b> 저장소 만들기</b> 클릭. 새 저장소는 기본 <b>비공개</b>인데 그대로 둬도 됩니다 —
              비공개 저장소는 <b>설정 → 협업자</b>에서 <code>edu-deploy-bot</code>을 초대(읽기 권한)해야
              플랫폼이 코드를 가져올 수 있습니다. (공개로 만들면 초대 없이 바로 됩니다.)</div>
          </div></div>
          <div className="g-step"><span className="g-no">3</span><div className="g-body">
            <div className="g-st">내 컴퓨터의 코드 파일 올리기 — 방법 A (웹 화면, 쉬움)</div>
            <div className="g-sd">저장소 화면에서 <b>파일 업로드(Upload File)</b>를 누르고 내 컴퓨터의 파일들을
              끌어다 놓은 뒤 <b>변경 사항 커밋</b>을 누르면 업로드됩니다. 프로그램 파일이 몇 개 안 되면
              이 방법이 가장 간단합니다.</div>
          </div></div>
          <div className="g-step"><span className="g-no">4</span><div className="g-body">
            <div className="g-st">내 컴퓨터의 코드 파일 올리기 — 방법 B (git 명령, 익숙한 분)</div>
            <div className="g-sd">프로젝트 폴더에서 아래 명령을 실행합니다. 아이디/비밀번호는 1번에서 발급한
              Gitea 계정입니다.
              <div className="code" style={{ marginTop: 8 }}>{`git init
git add .
git commit -m "최초 등록"
git remote add origin https://${host}/<내아이디>/<저장소이름>.git
git push -u origin main`}</div></div>
          </div></div>
        </div>

        <h4><span className="g-badge">2</span>2단계 · 설정 파일 2개 추가하기</h4>
        <p>저장소의 <b>맨 위 폴더(루트)</b>에 아래 두 파일을 새로 만들어 추가합니다.
          (Gitea 저장소 화면 <b>새 파일(New File)</b>에서 파일 이름과 내용을 입력하면 됩니다.)</p>

        <p style={{ marginTop: 14 }}><b>① service.yaml</b> — 아래 내용을 붙여넣고 내 프로그램에 맞게 값만 바꿉니다.</p>
        <div className="code">{`name: 출장 정산 자동 계산기
slug: travel-settlement
category: budget
purposes: [auto, gen]
tech: [Python]
summary: 출장 내역을 넣으면 정산액을 자동 계산합니다.
port: 8080
health: /healthz`}</div>
        <table className="g-table">
          <thead><tr><th>항목</th><th>의미</th><th>작성 규칙 / 예시</th></tr></thead>
          <tbody>
            <tr><td><code>name</code></td><td>화면에 표시될 프로그램 이름</td><td>한글 가능 · 예: 출장 정산 자동 계산기</td></tr>
            <tr><td><code>slug</code></td><td>주소·식별에 쓰는 영문 별명</td><td>영문 소문자·숫자·하이픈 · 예: travel-settlement</td></tr>
            <tr><td><code>category</code></td><td>업무 분야</td><td>doc(문서) · student(학생) · curri(교육과정) · budget(예산) · facil(시설) · data(데이터) · civil(민원) 중 하나</td></tr>
            <tr><td><code>summary</code></td><td>한 줄 소개</td><td>무슨 업무를 돕는지 한 문장</td></tr>
            <tr><td><code>port</code></td><td>프로그램이 열리는 포트 번호</td><td>보통 8080 (3단계 참고)</td></tr>
            <tr><td><code>health</code></td><td>정상 동작 확인용 주소</td><td>기본값 <code>/healthz</code> (3단계 참고)</td></tr>
          </tbody>
        </table>

        <p style={{ marginTop: 14 }}><b>② Dockerfile</b> — 실행 방법을 적는 파일입니다. 아래는 파이썬 예시입니다.</p>
        <div className="code">{`FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY . .
ENV PORT=8080
CMD ["python", "main.py"]`}</div>
        <div className="g-callout">
          <span className="g-ci"><Icon name="info" size={16} /></span>
          <span>Dockerfile 작성이 어렵다면 직접 쓰지 않아도 됩니다. 아래 <b>5단계</b>의 AI 요청 문장을
            그대로 복사해 AI 코딩 도구에 부탁하면 두 파일을 만들어 줍니다.</span>
        </div>

        <h4><span className="g-badge">3</span>3단계 · 꼭 지켜야 할 규칙 2가지</h4>
        <p>이 두 가지만 지키면 플랫폼이 프로그램을 안정적으로 실행합니다.</p>
        <p style={{ marginTop: 10 }}><b>규칙 1. 포트 번호를 고정하지 말고 <code>PORT</code>를 읽게 합니다.</b><br />
          프로그램이 열리는 포트를 코드에 숫자로 박아 두지 말고, 환경변수 <code>PORT</code> 값을 사용해야 합니다.</p>
        <div className="code">{`# 파이썬
import os
port = int(os.environ.get("PORT", "8080"))

// Node.js
const port = process.env.PORT || 8080;`}</div>
        <p style={{ marginTop: 10 }}><b>규칙 2. 상태 확인 주소 <code>/healthz</code>를 만듭니다.</b><br />
          누군가 <code>GET /healthz</code>로 접속하면 그냥 <code>ok</code>라고 응답하는 짧은 주소면 됩니다.
          플랫폼이 이 주소로 프로그램이 정상인지 확인합니다.</p>
        <div className="code">{`# 파이썬(FastAPI 예시)
@app.get("/healthz")
def healthz():
    return "ok"`}</div>

        <h4><span className="g-badge">4</span>4단계 · (선택) 내 컴퓨터에서 미리 확인</h4>
        <p>Docker가 설치돼 있다면, 등록 전에 아래 명령으로 직접 실행해 볼 수 있습니다. 어렵다면 건너뛰어도 됩니다.</p>
        <div className="code">{`docker build -t my-app .
docker run -e PORT=8080 -p 8080:8080 my-app
# 브라우저에서 http://localhost:8080/healthz 접속 → "ok" 가 보이면 성공`}</div>

        <h4><span className="g-badge">5</span>5단계 · 플랫폼에 등록하기</h4>
        <div className="g-steps">
          <div className="g-step"><span className="g-no">1</span><div className="g-body">
            <div className="g-st">저장소 주소 복사</div>
            <div className="g-sd">Gitea 저장소 주소(예: <code>{`https://${host}/내아이디/저장소`}</code>)를 복사합니다.
              저장소 화면의 주소창 주소를 그대로 쓰면 됩니다.</div>
          </div></div>
          <div className="g-step"><span className="g-no">2</span><div className="g-body">
            <div className="g-st">등록 화면에 붙여넣고 규격 검증</div>
            <div className="g-sd">이 등록 화면의 <b>레포 주소</b> 칸에 붙여넣고 <b>레포 규격 검증</b>을 누릅니다.
              통과(초록색)로 나오면 다음으로, 오류가 나오면 안내에 따라 파일을 고칩니다.</div>
          </div></div>
          <div className="g-step"><span className="g-no">3</span><div className="g-body">
            <div className="g-st">등록 요청</div>
            <div className="g-sd">이름·분야·공개 범위 등을 채우고 <b>등록 요청</b>을 누릅니다.</div>
          </div></div>
          <div className="g-step"><span className="g-no">4</span><div className="g-body">
            <div className="g-st">승인 후 자동 배포</div>
            <div className="g-sd">운영 관리자가 확인·승인하면 플랫폼이 코드를 가져와 <b>새 서비스로 배포</b>하고,
              그때부터 다른 직원들이 사용할 수 있습니다.</div>
          </div></div>
        </div>

        <h4><span className="g-badge">6</span>AI 코딩 도구에 부탁할 때 (복사해서 사용)</h4>
        <p>파일 만들기가 어렵다면, 사용하는 AI 도구에 아래 문장을 그대로 붙여넣으세요.</p>
        <div className="g-prompt">
          "내 프로젝트를 서비스로 배포하려고 해. 저장소 루트에 <b>service.yaml</b>과 <b>Dockerfile</b>을
          만들어 줘. 앱은 환경변수 <b>PORT</b>가 지정한 포트로 열리게 하고, <b>GET /healthz</b> 요청에
          200으로 'ok'를 응답하게 해 줘. service.yaml에는 name, slug, category, summary, port, health 항목을 넣어 줘."
        </div>

        <h4><span className="g-badge">7</span>자주 하는 실수</h4>
        <ul className="g-list x">
          <li>포트를 코드에 숫자로 고정함 → 환경변수 <code>PORT</code>를 읽도록 수정</li>
          <li><code>service.yaml</code>·<code>Dockerfile</code>을 하위 폴더에 둠 → 반드시 저장소 <b>맨 위(루트)</b></li>
          <li><code>/healthz</code> 주소가 없음 → 짧게 하나 추가</li>
          <li>비공개 저장소에 <code>edu-deploy-bot</code>을 초대하지 않음 → 협업자(읽기)로 추가하거나 저장소를 공개로 변경</li>
          <li>외부 저장소(GitHub 등) 주소를 등록함 → 내부 Gitea 주소만 등록 가능</li>
          <li>개인정보·비밀번호를 코드나 데이터에 포함 → 절대 올리지 않기</li>
        </ul>

        <h4><span className="g-badge">8</span>등록 전 최종 체크리스트</h4>
        <ul className="g-list">
          <li>내부 Gitea 에 저장소가 있고 주소를 안다 (비공개면 <code>edu-deploy-bot</code> 초대 완료)</li>
          <li>루트에 <code>service.yaml</code>이 있고 name·slug·category·port가 채워져 있다</li>
          <li>루트에 <code>Dockerfile</code>이 있다</li>
          <li>프로그램이 <code>PORT</code>로 열리고 <code>/healthz</code>가 <code>ok</code>를 응답한다</li>
          <li>민감한 정보가 저장소에 없다</li>
        </ul>

        <div className="g-callout warn">
          <span className="g-ci"><Icon name="warn" size={16} /></span>
          <span>바로 시험해 보고 싶다면, 등록 화면의 레포 주소 칸에
            <code> sample://travel-settlement</code>을 입력하고 <b>레포 규격 검증</b>을 눌러 보세요.
            실제 저장소 없이도 검증 과정을 미리 확인할 수 있습니다.</span>
        </div>
      </div>

      <div className="modal-foot">
        <button className="btn btn-primary" onClick={closeModal}>이해했어요, 등록하기</button>
      </div>
    </div>
  )
}
