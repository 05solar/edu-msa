# 표 데이터 통계 요약·차트 생성기 (data-summarizer)

설문·집계 데이터를 받은 **개인이 한 번에** 요약과 차트를 얻는 단발성 도구입니다.
업로드 → 요약/차트 → 저장으로 끝나며, 데이터 보관·공유 기능이 없습니다.

- **언어/기술**: Python (FastAPI, pandas, matplotlib)
- **분류(category)**: `data` 데이터
- **접속**: 플랫폼 "웹에서 바로 사용" → `http://data-summarizer.localhost`

## 기능
- CSV·엑셀 업로드 또는 CSV 붙여넣기(엑셀 복사)
- 열 유형 자동 판별(수치/범주), 열별 요약 통계(평균·표준편차·사분위·합계 / 고유값·빈도)
- 선택 열의 막대·분포(히스토그램)·원 차트 PNG 생성(한글 라벨) · 다운로드

## API
- `GET /healthz`
- `POST /api/analyze` (multipart file) → `{rowCount,columns,numeric,categorical,preview}`
- `POST /api/chart` (multipart file + column + kind) → `image/png`

## 로컬 실행
```bash
docker build -t data-summarizer .
docker run -p 8080:8080 data-summarizer
curl localhost:8080/healthz
```
