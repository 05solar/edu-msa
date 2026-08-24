# TEST.md · 백엔드 테스트 전략

## 자동 테스트

- `EduMsaApplicationTests` — 애플리케이션 컨텍스트 로드(H2, test 프로파일).
- Docker 빌드 시 `-x test`로 이미지 빌드를 빠르게 하고, 테스트는 별도로 수행 가능.

## 수동 검증 (compose 기동 후)

```bash
curl localhost:8080/api/health
curl localhost:8080/api/catalog
curl "localhost:8080/api/programs?sort=popular"
curl localhost:8080/api/programs/1
curl localhost:8080/api/programs/pending
curl -X POST localhost:8080/api/programs/9/review \
  -H 'Content-Type: application/json' -d '{"action":"approve","memo":"확인"}'
curl "localhost:8080/api/notifications?to=김도현"
```

## 체크리스트

- [ ] 시드 후 프로그램 16건, 사용자 7명, 알림/이력 존재.
- [ ] 목록 필터(cat/purpose/tech/scope/q)와 정렬(latest/popular/downloads) 동작.
- [ ] 상세에 readme/history/files/comments 포함.
- [ ] 등록(pending) 생성 시 운영자에게 알림 생성.
- [ ] 승인/반려/중지/재개 시 상태 전이 + 이력 + 알림.
- [ ] 권한 변경 반영.
- [ ] CORS로 프론트(localhost:5173)에서 호출 가능.
