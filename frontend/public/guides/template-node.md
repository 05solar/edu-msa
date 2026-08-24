# Node.js 스택 템플릿 (교육청 플랫폼 배포용)

`AI_BUILD_SPEC.md` 와 함께 사용하세요. 아래 파일을 저장소 루트에 만들면 바로 배포됩니다.

## service.yaml
```yaml
name: 내 노드 프로그램
slug: my-node-app
category: doc
purposes: [gen]
tech: [Node.js]
summary: 무엇을 하는 프로그램인지 한 줄 소개
port: 8080
health: /healthz
```

## Dockerfile
```dockerfile
FROM node:20-slim
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev || npm install --omit=dev
COPY . .
USER node
ENV PORT=8080
EXPOSE 8080
CMD ["node", "server.js"]
```

## package.json
```json
{
  "name": "my-node-app",
  "version": "1.0.0",
  "private": true,
  "main": "server.js",
  "dependencies": {}
}
```

## server.js (표준 http 모듈만 사용)
```js
const http = require("http");
const port = process.env.PORT || 8080;   // 반드시 PORT 사용

const server = http.createServer((req, res) => {
  if (req.url.startsWith("/healthz")) {
    res.writeHead(200, { "Content-Type": "text/plain" });
    res.end("ok");
  } else if (req.url === "/") {
    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
    res.end("<h1>내 프로그램</h1><p>여기에 기능을 만드세요.</p>");
  } else {
    res.writeHead(404);
    res.end("not found");
  }
});

server.listen(port, "0.0.0.0", () => console.log(`listening on :${port}`));
```

## Express 를 쓰고 싶다면
```json
{ "dependencies": { "express": "^4" } }
```
```js
const express = require("express");
const app = express();
app.get("/healthz", (req, res) => res.send("ok"));
app.get("/", (req, res) => res.send("내 프로그램"));
app.listen(process.env.PORT || 8080, "0.0.0.0");
```

## 로컬 확인
```bash
docker build -t my-node-app .
docker run -e PORT=8080 -p 8080:8080 my-node-app
curl localhost:8080/healthz   # ok
```
