# 시설 점검 체크리스트 (facil)

표준 점검 항목을 체크하면 완료율과 미점검 항목을 정리하고 적합 여부를 판정합니다.

## 실행
```bash
docker build -t facility-check .
docker run -e PORT=8080 -p 8080:8080 facility-check
# http://localhost:8080  ·  /healthz → ok
```
