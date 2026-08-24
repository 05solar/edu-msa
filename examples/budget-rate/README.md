# 예산 집행률 계산기 (budget)

예산액과 집행액을 입력하면 집행률·잔액과 집행 상태(정상/저조/초과)를 계산합니다.

## 실행
```bash
docker build -t budget-rate .
docker run -e PORT=8080 -p 8080:8080 budget-rate
# http://localhost:8080  ·  /healthz → ok
```
