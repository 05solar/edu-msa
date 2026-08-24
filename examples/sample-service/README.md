# 예제 · 출장 정산 자동 계산기 (표준 서비스 규격)

바이브 코더가 [VIBE_CODING_GUIDE.md](../../docs/VIBE_CODING_GUIDE.md)와
[MSA_SERVICE_SPEC.md](../../docs/MSA_SERVICE_SPEC.md)를 지켰을 때의 최소 예시다.
이 폴더를 그대로 GitHub 레포에 올리고 주소를 플랫폼에 등록하면 새 서비스로 배포된다.

## 규격 준수 포인트

- 루트에 `service.yaml`(name·slug·category·port·health) 존재
- 루트에 `Dockerfile` 존재, 비루트 실행
- 앱이 `PORT` 환경변수 포트로 listen (하드코딩 금지)
- `GET /healthz` → 200 `ok`
- 외부 라이브러리 없이 표준 라이브러리만 사용 (빠른 빌드)

## 로컬 실행

```bash
docker build -t travel-settlement .
docker run -e PORT=8080 -p 8080:8080 travel-settlement
curl localhost:8080/healthz                       # ok
curl "localhost:8080/api/settle?days=2&nights=1&transport=48000"
```
