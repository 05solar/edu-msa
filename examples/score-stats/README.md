# 성적 통계 계산기 (student)

점수 목록을 입력하면 평균·최고·최저·중앙값·표준편차와 A~F 등급 분포를 계산합니다.

## 실행
```bash
docker build -t score-stats .
docker run -e PORT=8080 -p 8080:8080 score-stats
# http://localhost:8080  ·  /healthz → ok
```
