# 기안문 서식 생성기 (doc)

제목·부서·기안자·본문을 입력하면 표준 기안문 형식 텍스트로 정리해 줍니다.

## 실행
```bash
docker build -t doc-formatter .
docker run -e PORT=8080 -p 8080:8080 doc-formatter
# http://localhost:8080  ·  http://localhost:8080/healthz → ok
```
