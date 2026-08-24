# 파이썬 스택 템플릿 (교육청 플랫폼 배포용)

`AI_BUILD_SPEC.md` 와 함께 사용하세요. 아래 파일 3개를 저장소 루트에 만들면
바로 배포되는 최소 프로젝트가 됩니다. (외부 라이브러리 없이 표준 라이브러리만 사용)

## service.yaml
```yaml
name: 내 파이썬 프로그램
slug: my-python-app
category: data
purposes: [analyze]
tech: [Python]
summary: 무엇을 하는 프로그램인지 한 줄 소개
port: 8080
health: /healthz
```

## Dockerfile
```dockerfile
FROM python:3.11-slim
WORKDIR /app
# 외부 패키지가 필요하면 아래 두 줄 사용 (requirements.txt 준비)
# COPY requirements.txt .
# RUN pip install --no-cache-dir -r requirements.txt
COPY . .
RUN useradd -r -u 1001 appuser
USER appuser
ENV PORT=8080
EXPOSE 8080
CMD ["python", "main.py"]
```

## main.py (표준 라이브러리만 사용하는 예시)
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
            self._send(200, "<h1>내 프로그램</h1><p>여기에 기능을 만드세요.</p>",
                       "text/html; charset=utf-8")
        else:
            self._send(404, "not found")

    def log_message(self, *a):
        print(self.address_string(), a[0] % a[1:])


def main():
    port = int(os.environ.get("PORT", "8080"))   # 반드시 PORT 사용
    print(f"listening on :{port}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()
```

## FastAPI 를 쓰고 싶다면
```txt
# requirements.txt
fastapi
uvicorn
```
```python
# main.py
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
Dockerfile 의 `CMD` 는 `["python", "main.py"]` 그대로 두면 됩니다.

## 로컬 확인
```bash
docker build -t my-python-app .
docker run -e PORT=8080 -p 8080:8080 my-python-app
curl localhost:8080/healthz   # ok
```
