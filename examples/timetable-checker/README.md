# 시간표 충돌 검사·이미지 생성기 (timetable-checker)

시간표를 짜는 **개인이 한 번에** 충돌을 점검하고 배포용 시간표 이미지를 얻는 단발성 도구입니다.
입력 → 충돌 검사 → 이미지 생성/저장으로 끝나며, 저장·공유·보고 기능이 없습니다.

- **언어/기술**: TypeScript (Fastify, 런타임 tsx)
- **분류(category)**: `curri` 교육과정
- **접속**: 플랫폼 "웹에서 바로 사용" → `http://timetable-checker.localhost`

## 기능
- 수업 입력(요일,교시,학급,과목,교사,교실)
- 같은 요일·교시의 **교사/교실/학급 중복(충돌)** 검출 및 상세 표시
- 학급·교사·교실 기준 주간 시간표 SVG 생성(충돌 셀 강조) · SVG 다운로드

## API
- `GET /healthz`
- `POST /api/check` `{sessions:[{day,period,klass,subject,teacher,room}]}` → `{conflicts,keys,count}`
- `POST /api/timetable` `{sessions,view,key}` → `{svg}`

## 로컬 실행
```bash
docker build -t timetable-checker .
docker run -p 8080:8080 timetable-checker
curl localhost:8080/healthz
```
