# 민원 답변 초안 생성기 (civil)

민원 유형·성함·내용을 입력하면 정중한 답변 초안 텍스트를 만들어 줍니다. (발송 전 담당자 검토 필요)

## 실행
```bash
docker build -t civil-reply .
docker run -e PORT=8080 -p 8080:8080 civil-reply
# http://localhost:8080  ·  /healthz → ok
```
