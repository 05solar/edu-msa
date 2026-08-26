# TEST.md · 백엔드 테스트 전략

## 자동 테스트

- `EduMsaApplicationTests` — 애플리케이션 컨텍스트 로드(H2, test 프로파일).
- Docker 빌드 시 `-x test`로 이미지 빌드를 빠르게 하고, 테스트는 별도로 수행 가능.

## 수동 검증 (compose 기동 후)

compose는 backend를 `:8088`에 노출한다. 대부분의 엔드포인트는 로그인(JWT)이 필요하므로
먼저 auth-service(`:8089`)에서 토큰을 받아 `Authorization: Bearer`로 실어 호출한다.

```bash
# 공개 엔드포인트 (토큰 불필요)
curl localhost:8088/api/health
curl localhost:8088/api/catalog

# 로그인 토큰 확보 (데모 로그인 예)
TOKEN=$(curl -s -X POST localhost:8089/api/auth/demo-login \
  -H 'Content-Type: application/json' -d '{"role":"admin"}' | jq -r .accessToken)

# 로그인 사용자 등급
curl -H "Authorization: Bearer $TOKEN" "localhost:8088/api/programs?sort=popular"
curl -H "Authorization: Bearer $TOKEN" localhost:8088/api/programs/1

# ADMIN 등급 — 검토/배포/사용자
curl -H "Authorization: Bearer $TOKEN" localhost:8088/api/programs/pending
curl -X POST localhost:8088/api/programs/1/review \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"action":"stop","memo":"확인"}'
curl -H "Authorization: Bearer $TOKEN" localhost:8088/api/users
```

## 체크리스트

- [ ] 시드 후 프로그램 7건(7개 내장 서비스), 사용자 7명.
- [ ] 목록 필터(cat/purpose/tech/scope/q)와 정렬(latest/popular/downloads) 동작.
- [ ] 상세에 readme/history/files/comments 포함.
- [ ] 인가(RBAC): 토큰 없이 보호 경로 호출 시 401, 등급 부족 시 403.
- [ ] `POST /api/programs`는 CODER 이상, 검토/권한/배포/사용자 관리는 ADMIN만 200.
- [ ] 승인/반려/중지/재개 시 상태 전이 + 이력 + 알림.
- [ ] 배포(작업 큐) 적재 후 워커가 처리, `/api/programs/{id}/deployment`에 상태 반영.
- [ ] CORS로 프론트(localhost:5173)에서 호출 가능(자격 증명 포함).
