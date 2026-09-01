# 바이브 코딩 가이드 · 내 프로그램을 교육청 플랫폼 서비스로 올리기

> **이 문서를 먼저 읽고, 바이브 코딩 도구(AI)에게 이렇게 말하세요.**
>
> "첨부한 `VIBE_CODING_GUIDE.md`와 `MSA_SERVICE_SPEC.md` 문서를 먼저 읽고, 이 규칙에
> 맞게 프로젝트를 만들어 줘."
>
> 그러면 어떤 언어로 만들든 교육청 공유 플랫폼에 **새로운 서비스로 바로 등록**할 수
> 있습니다.

> **더 간편하게**: 플랫폼의 "프로그램 등록 → 등록 가이드"에서 **AI 빌드 지시서**
> (`AI_BUILD_SPEC.md`)와 스택별 템플릿(파이썬/Node/정적)을 내려받아 AI에 첨부하면,
> 이 규격에 맞는 프로젝트가 바로 만들어집니다. (파일: `frontend/public/guides/`)

---

## 1. 큰 그림 (왜 이런 규칙이 필요한가)

여러분이 만든 코드는 교육청 플랫폼이 **자동으로 내려받아 컨테이너로 감싸 서비스로
띄웁니다.** 사람이 코드를 하나하나 고쳐서 올리는 게 아니라, 기계가 규칙에 맞는지
확인하고 자동으로 배포합니다. 그래서 "기본 프로그램은 이래야 한다"는 최소 규칙이
필요합니다. 규칙은 딱 5가지입니다.

1. GitHub 레포지토리 하나 = 서비스 하나
2. 레포 루트에 `service.yaml` (서비스 설명서)
3. 레포 루트에 `Dockerfile` (실행 방법)
4. 환경변수 `PORT`로 열리는 웹 서버 하나
5. 헬스 체크 경로 `GET /healthz` 응답

이 5가지만 지키면 언어(Python, Node, Go, Java …)는 자유입니다.

---

## 2. 5가지 규칙 자세히

### 규칙 1. GitHub 레포 = 서비스 하나

- 프로그램 하나를 **공개 GitHub 레포지토리** 하나에 담습니다.
- 그 레포 주소(예: `https://github.com/yourname/my-tool`)를 플랫폼의
  "프로그램 등록" 화면에 붙여넣으면 됩니다.
- 플랫폼은 지정한 브랜치(기본 `main`)의 코드를 내려받습니다.

### 규칙 2. `service.yaml` — 서비스 설명서 (필수)

레포 **루트**에 아래 파일을 둡니다. 플랫폼이 이 파일을 읽어 서비스 이름·분류·포트를
파악합니다.

```yaml
# service.yaml
name: 출장 정산 자동 계산기        # 서비스 이름 (한글 가능)
slug: travel-settlement          # URL/식별용 영문 소문자-하이픈
category: budget                 # 업무 분야 (아래 표 참고)
purposes: [auto, gen]            # 기능 유형 (아래 표 참고)
tech: [Python, Streamlit]        # 사용 기술 (자유 표기)
summary: 출장 내역 엑셀을 넣으면 정산액을 자동 계산합니다.  # 한 줄 소개
port: 8080                       # 앱이 여는 포트 (Dockerfile과 일치)
health: /healthz                 # 헬스 체크 경로 (기본 /healthz)
```

**category(업무 분야)** 값: `doc`(문서·공문) · `student`(학생·성적) ·
`curri`(교육과정) · `budget`(예산·회계) · `facil`(시설·안전) · `data`(데이터) ·
`civil`(민원)

**purposes(기능 유형)** 값: `auto`(자동화) · `gen`(생성) · `verify`(검증) ·
`analyze`(분석) · `summary`(요약) · `search`(검색) · `dash`(대시보드)

### 규칙 3. `Dockerfile` — 실행 방법 (필수)

플랫폼은 언어를 모릅니다. 대신 `Dockerfile`을 보고 여러분의 앱을 실행합니다.
아래는 언어별 최소 예시입니다.

**Python (Streamlit/FastAPI 등)**
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
ENV PORT=8080
EXPOSE 8080
CMD ["python", "main.py"]      # main.py가 $PORT 포트로 서버를 연다
```

**Node.js**
```dockerfile
FROM node:20-slim
WORKDIR /app
COPY package*.json .
RUN npm ci --omit=dev
COPY . .
ENV PORT=8080
EXPOSE 8080
CMD ["node", "server.js"]
```

### 규칙 4. `PORT` 환경변수로 여는 웹 서버

- 앱은 반드시 환경변수 `PORT`(문자열)로 지정된 포트에서 요청을 받아야 합니다.
- 포트 번호를 코드에 고정하지 말고 `PORT`를 읽으세요.

```python
# Python 예시
import os
port = int(os.environ.get("PORT", "8080"))
```
```js
// Node 예시
const port = process.env.PORT || 8080;
```

### 규칙 5. 헬스 체크 `GET /healthz`

- 플랫폼은 서비스가 살아있는지 `GET /healthz` 로 확인합니다.
- 200 응답에 짧은 본문(예: `ok`)만 주면 됩니다.

```python
# FastAPI 예시
@app.get("/healthz")
def healthz():
    return "ok"
```

---

## 3. 권장 파일 구조

```
my-tool/
├── service.yaml          # 필수: 서비스 설명서
├── Dockerfile            # 필수: 실행 방법
├── README.md             # 권장: 프로그램 소개 (플랫폼 상세화면에 표시)
├── requirements.txt      # (파이썬인 경우)
├── main.py               # 앱 진입점, $PORT 로 서버 오픈, /healthz 제공
└── src/ …                # 기능별로 폴더 분리 권장
```

`README.md`는 플랫폼 상세 화면의 "소개" 탭에 표시됩니다. 개요/설치/사용 방법/주의
사항 순서로 작성하면 좋습니다.

---

## 4. 자가 점검 체크리스트

등록 전에 스스로 확인하세요. (플랫폼도 등록 시 자동으로 검사합니다.)

- [ ] 공개 GitHub 레포가 있고 주소를 안다
- [ ] 루트에 `service.yaml`이 있고 `name`, `slug`, `category`, `port`가 채워져 있다
- [ ] 루트에 `Dockerfile`이 있다
- [ ] 앱이 `PORT` 환경변수 포트로 열린다
- [ ] `GET /healthz`가 200을 준다
- [ ] `docker build`와 로컬 실행이 성공한다 (선택이지만 강력 권장)
- [ ] 개인정보/민감정보를 코드나 데이터에 넣지 않았다

## 5. 흔한 실수

- 포트를 코드에 `8501` 처럼 고정 → `PORT` 환경변수를 읽도록 수정.
- `service.yaml`을 하위 폴더에 둠 → 반드시 레포 **루트**.
- 헬스 경로 없음 → `/healthz` 추가.
- 비공개 레포 → 공개로 전환하거나 접근 토큰 등록(플랫폼 안내에 따름).

## 6. 등록하는 법

1. 위 규칙에 맞춰 GitHub에 push 합니다.
2. 플랫폼에 로그인합니다. **프로그램 등록에는 "바이브 코더(CODER)" 권한이 필요**합니다.
   가입은 항상 일반 사용자(USER)로 되며, 마이페이지에서 코더 권한을 신청하고 운영 관리자
   승인을 받으면 등록할 수 있습니다.
3. 플랫폼 → "프로그램 등록" → GitHub 레포 주소 입력 → 브랜치 선택.
4. 플랫폼이 규격을 검사하고, 운영 관리자 승인·배포 후 새 서비스가 공개됩니다.
5. 공개된 서비스는 포트가 아니라 **서브도메인 `http://<slug>.localhost`**(로컬 기준)로 열려
   목록의 "웹에서 바로 사용"으로 바로 접속합니다.

기술 규격의 정확한 정의는 [MSA_SERVICE_SPEC.md](../architecture/MSA_SERVICE_SPEC.md)를 참고하세요.
