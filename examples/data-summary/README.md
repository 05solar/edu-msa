# 데이터 요약 통계 (data)

숫자 데이터를 붙여넣으면 개수·합계·평균·중앙값·최소·최대를 요약합니다.

## 실행
```bash
docker build -t data-summary .
docker run -e PORT=8080 -p 8080:8080 data-summary
# http://localhost:8080  ·  /healthz → ok
```
