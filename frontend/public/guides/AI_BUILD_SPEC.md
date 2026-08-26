# AI 빌드 지시서 (완결판) · 교육청 플랫폼에 배포되는 프로그램 만들기

이 문서 **하나만** AI 코딩 도구에 첨부하면, 어떤 AI든 이 규격에 맞는 **바로 배포 가능한
프로젝트**를 만들 수 있습니다. 다른 문서를 함께 볼 필요 없이 이 파일로 완결됩니다.

---

## 0. AI에게 주는 지시 (이 문단을 그대로 복사해서 사용하세요)

> 너는 아래 "배포 계약"을 100% 만족하는 **실행 가능한 프로젝트 전체**를 만든다.
> 내가 만들 프로그램은 **《여기에 하고 싶은 것을 적으세요. 예: 출장 여비를 계산하는 웹앱》**.
> 규칙:
> 1) 저장소 **루트**에 `service.yaml` 과 `Dockerfile` 을 만든다.
> 2) 앱은 환경변수 **`PORT`** 가 준 포트에서 HTTP를 받는다(포트 하드코딩 금지).
> 3) **`GET /healthz`** 요청에 200과 본문 `ok` 를 응답한다.
> 4) 아래 "스택별 완성 템플릿" 중 하나를 골라 그 파일들을 실제 내용까지 전부 만든다.
> 5) 마지막에 `docker build` 와 `docker run` 으로 스스로 동작을 점검하고, 만든 파일
>    목록과 각 파일 전체 내용을 출력한다.

---

## 1. 배경 (왜 이렇게 만드나)

내가 만든 코드를 GitHub 공개 저장소에 올리면, 교육청 플랫폼이 그 코드를 자동으로
가져와 **컨테이너로 실행**하고, 운영 관리자가 승인하면 다른 직원이 웹에서 바로 씁니다.
플랫폼은 언어를 모르기 때문에, "이건 무슨 프로그램인가(`service.yaml`)"와 "어떻게
실행하나(`Dockerfile`)"를 표준 방식으로 알려 줘야 합니다.

배포되면 서비스는 포트가 아니라 **서브도메인** `http://<slug>.localhost` 로 열립니다
(리버스 프록시가 `slug` 로 라우팅). 플랫폼은 컨테이너 `/healthz` 가 정상 응답할 때까지
기다렸다가 "실행 중"으로 표시하므로, 첫 접속에서 오류가 나지 않게 하려면 헬스체크를
꼭 구현하세요.

## 2. 배포 계약 (반드시 지킬 것 · 테스트 가능한 조건)

1. 저장소 **루트**에 `service.yaml` 존재. `name`·`slug`·`category`·`port` 필수.
2. `slug` 는 정규식 `^[a-z][a-z0-9-]{1,38}$` (영문 소문자로 시작, 소문자/숫자/하이픈).
3. `category` 는 다음 중 하나: `doc` `student` `curri` `budget` `facil` `data` `civil`.
4. 저장소 **루트**에 `Dockerfile` 존재.
5. 앱은 환경변수 **`PORT`** 포트에서 listen (예: `8080`). 코드에 포트 숫자 하드코딩 금지.
6. **`GET /healthz`** → HTTP 200, 본문 `ok`.
7. 컨테이너는 비루트 사용자로 실행(권장). 로그는 표준출력(stdout).
8. 비밀번호·토큰·개인정보를 코드/데이터에 넣지 않는다.
9. 공개(Public) 저장소여야 한다.

## 3. 산출물 (저장소 루트에 두는 파일)

```
<프로젝트>/
├── service.yaml     # 필수 · 프로그램 설명
├── Dockerfile       # 필수 · 실행 방법
├── README.md        # 권장 · 프로그램 소개(상세 화면에 표시)
├── (앱 진입점)       # main.py / server.js / index.html 등
└── src/ …           # 기능별로 폴더 분리 권장
```

## 4. `service.yaml` 필드 레퍼런스

```yaml
name: 프로그램 표시 이름            # 필수. 한글 가능
slug: my-program                  # 필수. ^[a-z][a-z0-9-]{1,38}$ (전체에서 유일)
category: doc                     # 필수. doc|student|curri|budget|facil|data|civil
purposes: [auto, gen]             # 선택. auto|gen|verify|analyze|summary|search|dash
tech: [Python]                    # 선택. 사용 기술 자유 표기
summary: 한 줄 소개                # 선택. 무슨 업무를 돕는지
port: 8080                        # 필수. 앱이 여는 포트 (Dockerfile 과 일치)
health: /healthz                  # 선택. 상태 확인 경로 (기본 /healthz)
resources:                        # 선택
  cpu: "250m"
  memory: "256Mi"
```

| category | 업무 분야 | | category | 업무 분야 |
| --- | --- | --- | --- | --- |
| `doc` | 문서·공문 | | `facil` | 시설·안전 |
| `student` | 학생·성적 | | `data` | 데이터 |
| `curri` | 교육과정 | | `civil` | 민원 |
| `budget` | 예산·회계 | | | |

## 5. 런타임 규칙 (앱 코드에서 반드시)

### 5-1. 포트는 `PORT` 환경변수 사용
```python
import os
port = int(os.environ.get("PORT", "8080"))   # Python
```
```js
const port = process.env.PORT || 8080;        // Node.js
```

### 5-2. `GET /healthz` → `ok`
```python
# FastAPI
@app.get("/healthz")
def healthz():
    return "ok"
```
```js
// Express
app.get("/healthz", (req, res) => res.send("ok"));
```

### 5-3. 오류는 통일된 형식(JSON)으로

API 성격의 응답에서 오류를 낼 때는 다음 형식을 씁니다(HTTP 상태 코드도 함께 설정).

```json
{ "error": { "code": "BAD_INPUT", "message": "사람이 읽을 설명" } }
```

```python
# FastAPI
from fastapi.responses import JSONResponse
def err(code, message, status=400):
    return JSONResponse({"error": {"code": code, "message": message}}, status_code=status)
```

---

## 6. 스택별 완성 템플릿 (하나 골라 그대로 만들기)

> 아래 각 템플릿은 **그 파일들만 만들면 바로 빌드·배포되는 최소 완성본**입니다.
> 프로그램 로직만 `/` 나 `/api/...` 핸들러에 채워 넣으면 됩니다.

### A. Python — 표준 라이브러리만 (가장 빠른 빌드, 의존성 없음)

**service.yaml**
```yaml
name: 내 파이썬 프로그램
slug: my-python-app
category: data
purposes: [analyze]
tech: [Python]
summary: 무엇을 하는지 한 줄 소개
port: 8080
health: /healthz
```
**Dockerfile**
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY . .
RUN useradd -r -u 1001 appuser
USER appuser
ENV PORT=8080
EXPOSE 8080
CMD ["python", "main.py"]
```
**main.py**
```python
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body, ctype="text/plain; charset=utf-8"):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path.startswith("/healthz"):
            self._send(200, "ok")
        elif self.path == "/":
            self._send(200, "<h1>내 프로그램</h1>", "text/html; charset=utf-8")
        else:
            self._send(404, "not found")

    def log_message(self, *a):
        print(self.address_string(), a[0] % a[1:])


def main():
    port = int(os.environ.get("PORT", "8080"))
    print(f"listening on :{port}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()
```

### B. Python — FastAPI (라이브러리 사용)

**requirements.txt**
```txt
fastapi
uvicorn
```
**Dockerfile**
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
RUN useradd -r -u 1001 appuser
USER appuser
ENV PORT=8080
EXPOSE 8080
CMD ["python", "main.py"]
```
**main.py**
```python
import os
from fastapi import FastAPI
import uvicorn

app = FastAPI()

@app.get("/healthz")
def healthz():
    return "ok"

@app.get("/")
def index():
    return {"message": "내 프로그램"}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", "8080")))
```
service.yaml 은 A와 동일 형식(`tech: [Python, FastAPI]`).

### C. Node.js — 내장 http (의존성 없음)

**service.yaml**
```yaml
name: 내 노드 프로그램
slug: my-node-app
category: doc
purposes: [gen]
tech: [Node.js]
summary: 무엇을 하는지 한 줄 소개
port: 8080
health: /healthz
```
**Dockerfile**
```dockerfile
FROM node:20-slim
WORKDIR /app
COPY . .
USER node
ENV PORT=8080
EXPOSE 8080
CMD ["node", "server.js"]
```
**server.js**
```js
const http = require("http");
const port = process.env.PORT || 8080;

const server = http.createServer((req, res) => {
  if (req.url.startsWith("/healthz")) {
    res.writeHead(200, { "Content-Type": "text/plain" });
    res.end("ok");
  } else if (req.url === "/") {
    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
    res.end("<h1>내 프로그램</h1>");
  } else {
    res.writeHead(404);
    res.end("not found");
  }
});
server.listen(port, "0.0.0.0", () => console.log(`listening on :${port}`));
```

### D. Node.js — Express

**package.json**
```json
{ "name": "my-node-app", "version": "1.0.0", "private": true,
  "main": "server.js", "dependencies": { "express": "^4" } }
```
**Dockerfile**
```dockerfile
FROM node:20-slim
WORKDIR /app
COPY package*.json ./
RUN npm install --omit=dev
COPY . .
USER node
ENV PORT=8080
EXPOSE 8080
CMD ["node", "server.js"]
```
**server.js**
```js
const express = require("express");
const app = express();
app.get("/healthz", (req, res) => res.send("ok"));
app.get("/", (req, res) => res.send("내 프로그램"));
app.listen(process.env.PORT || 8080, "0.0.0.0");
```

### E. 정적 웹 페이지 (HTML/CSS/JS · nginx)

정적 사이트는 포트가 **80** 고정입니다. `service.yaml` 의 `port` 를 `80` 으로 두세요.

**service.yaml**
```yaml
name: 내 웹 페이지
slug: my-static-site
category: civil
purposes: [gen]
tech: [HTML]
summary: 무엇을 안내/처리하는 페이지인지
port: 80
health: /healthz
```
**Dockerfile**
```dockerfile
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY site/ /usr/share/nginx/html
EXPOSE 80
```
**nginx.conf**
```nginx
server {
  listen 80;
  root /usr/share/nginx/html;
  index index.html;
  location = /healthz { return 200 "ok"; add_header Content-Type text/plain; }
  location / { try_files $uri $uri/ /index.html; }
}
```
**site/index.html**
```html
<!doctype html><html lang="ko"><meta charset="utf-8">
<title>내 웹 페이지</title>
<h1>내 웹 페이지</h1>
```

---

## 7. 자가 점검 (AI가 만든 뒤 반드시 확인)

```bash
docker build -t myapp .
docker run -e PORT=8080 -p 8080:8080 myapp
# 다른 터미널에서:
curl localhost:8080/healthz     # → ok  (이게 나와야 성공)
```

## 8. 하지 말 것 (자주 하는 실수)

- 포트 하드코딩(`8501` 등) → `PORT` 환경변수 사용
- `service.yaml`/`Dockerfile` 을 하위 폴더에 둠 → 반드시 **루트**
- `/healthz` 없음 → 추가
- 비공개 저장소 → 공개(Public)
- 비밀번호·토큰·개인정보 커밋

## 9. AI 산출 형식 (이렇게 내놔 달라)

1. 만든 **파일 목록**(경로)을 먼저 보여준다.
2. 각 파일의 **전체 내용**을 코드블록으로 보여준다.
3. `service.yaml` 의 `slug`·`category`·`port` 가 계약(2장)을 만족하는지 한 줄로 확인한다.
4. `docker build`·`docker run`·`curl /healthz` 결과(또는 예상 결과)를 적는다.

## 10. 최종 체크리스트

- [ ] 공개 GitHub 저장소 준비, 주소 확인
- [ ] 루트 `service.yaml` (name·slug·category·port)
- [ ] 루트 `Dockerfile`
- [ ] 앱이 `PORT` 로 열림, `GET /healthz` → `ok`
- [ ] `docker build`/`docker run` 성공
- [ ] 민감정보 없음

이 6가지가 모두 충족되면, 저장소 주소를 플랫폼 "프로그램 등록"에 입력 →
**레포 규격 검증** → **등록 요청** → 운영 관리자 승인 시 자동으로 컨테이너가 떠서
`http://<slug>.localhost` 주소로 서비스가 공개됩니다.
