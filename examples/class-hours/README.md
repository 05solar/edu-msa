# 교육과정 시수 계산기 (curri)

주당 시수와 연간 수업 주수를 입력하면 학기·연간 총 시수를 계산합니다.

## 실행
```bash
docker build -t class-hours .
docker run -e PORT=8080 -p 8080:8080 class-hours
# http://localhost:8080  ·  /healthz → ok
```
