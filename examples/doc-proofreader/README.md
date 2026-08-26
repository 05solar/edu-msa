# 공문서 오타·맞춤법 검사기 (doc-proofreader)

공문·안내문을 발송하기 전에 **개인이 한 번에** 오타를 점검하는 단발성 도구입니다.
멀티유저·상태공유·보고 기능이 없으며, 접속 → 붙여넣기 → 검사 → 교정본 저장으로 끝납니다.

- **언어/기술**: Go (net/http, 외부 의존성 없음 · 오프라인 규칙기반)
- **분류(category)**: `doc` 문서·공문
- **접속**: 플랫폼 "웹에서 바로 사용" → `http://doc-proofreader.localhost`

## 기능
- 자주 틀리는 맞춤법·행정 오탈자 사전 교정 (몇일→며칠, 읍니다→습니다, 역활→역할 등)
- 띄어쓰기 교정 (제출바랍니다→제출 바랍니다, 할수있→할 수 있 등)
- 공백 정리 (연속 공백, 문장부호 앞 공백, 줄 끝 공백)
- 동음이의 행정용어 문맥 확인 표시 (결재/결제, 지양/지향, 갱신/경신 등 — 자동교정 안 함)
- 교정본 복사·다운로드(.txt)

## API
- `GET /healthz` — 상태 점검
- `POST /api/check` `{ "text": "..." }` → `{ issues:[{original,suggestion,rule,kind,count,context}], corrected, stats }`

## 로컬 실행
```bash
docker build -t doc-proofreader .
docker run -p 8080:8080 doc-proofreader
curl localhost:8080/healthz
```
