# 정적 웹 페이지 템플릿 (교육청 플랫폼 배포용)

HTML/CSS/JS 로 만든 정적 페이지(계산기, 안내 페이지 등)를 배포할 때 사용합니다.
nginx 로 서빙하며, 상태 확인용 `/healthz` 응답만 하나 추가하면 됩니다.
배포·승인되면 서비스는 `http://<slug>.localhost` 주소(서브도메인)로 열립니다.

## service.yaml
```yaml
name: 내 웹 페이지
slug: my-static-site
category: civil
purposes: [gen]
tech: [HTML]
summary: 무엇을 안내/처리하는 페이지인지 한 줄 소개
port: 80
health: /healthz
```

## Dockerfile
```dockerfile
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY site/ /usr/share/nginx/html
EXPOSE 80
# nginx 는 기본 80 포트. 플랫폼이 PORT 를 80 으로 맞춰 준다.
```

## nginx.conf
```nginx
server {
  listen 80;
  root /usr/share/nginx/html;
  index index.html;
  location = /healthz { return 200 "ok"; add_header Content-Type text/plain; }
  location / { try_files $uri $uri/ /index.html; }
}
```

## site/index.html
```html
<!doctype html>
<html lang="ko"><meta charset="utf-8">
<title>내 웹 페이지</title>
<h1>내 웹 페이지</h1>
<p>여기에 내용을 만드세요.</p>
```

> 참고: 정적 사이트는 앱 포트가 80 으로 고정입니다. `service.yaml` 의 `port` 를 `80` 으로
> 두세요. (동적 서버가 아니라 별도의 `PORT` 환경변수 처리는 필요 없습니다.)

## 로컬 확인
```bash
docker build -t my-static-site .
docker run -p 8080:80 my-static-site
curl localhost:8080/healthz   # ok
```
