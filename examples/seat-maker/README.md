# 학생 자리배치 생성기 (seat-maker)

담임이 자리를 새로 정할 때 **개인이 한 번에** 좌석 배치안을 만드는 단발성 도구입니다.
명단 → 조건 → 배치 → 엑셀/인쇄로 끝나며, 상태 저장·공유·보고 기능이 없습니다.

- **언어/기술**: Python (FastAPI, openpyxl)
- **분류(category)**: `student` 학생·성적
- **접속**: 플랫폼 "웹에서 바로 사용" → `http://seat-maker.localhost`

## 기능
- 명단 입력: 텍스트(이름 또는 "이름,남/여") 또는 엑셀/CSV 업로드
- 배치 방식: 제비뽑기(무작위) · 남녀 균형(교차) · 입력 순서
- 인접 금지 조건(특정 학생 쌍을 옆자리 배제, 로컬 스왑 보정)
- 교탁 방향 좌석표 미리보기 · 다시 섞기
- 엑셀(.xlsx) 다운로드 · 브라우저 인쇄

## API
- `GET /healthz`
- `POST /api/arrange` `{students,rows,cols,method,separate,fixed,seed}` → `{grid,unplaced,separateSatisfied}`
- `POST /api/parse-upload` (multipart file) → `{students}`
- `POST /api/export` `{title,grid}` → xlsx 파일

## 로컬 실행
```bash
docker build -t seat-maker .
docker run -p 8080:8080 seat-maker
curl localhost:8080/healthz
```
