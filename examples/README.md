# examples · 교육청 기본 서비스 (7개 · 카테고리별 1개)

edu-msa 플랫폼에 기본 내장되는 **개인용 단발 도구 7개**입니다. 교육청 전 직원이 필요할 때
접속해 한 번의 작업(검사·변환·생성·계산·추출)을 처리하고 끝내는 도구이며,
**여러 사용자가 상호작용하며 상태를 관리하거나 보고를 진행하는 협업 시스템이 아닙니다.**
7개 업무 분야(category) 각각에 정확히 하나의 서비스가 존재합니다.

## 서비스 목록

| 카테고리 | 서비스 | slug | 언어 | 한 번에 처리하는 작업 |
|---|---|---|---|---|
| doc 문서·공문 | 공문서 오타·맞춤법 검사기 | `doc-proofreader` | Go | 텍스트 붙여넣기 → 맞춤법·띄어쓰기 교정본 |
| student 학생·성적 | 학생 자리배치 생성기 | `seat-maker` | Python | 명단 업로드 → 조건 반영 좌석 배치 → 엑셀 |
| curri 교육과정 | 시간표 충돌 검사·이미지 생성기 | `timetable-checker` | TypeScript | 시간표 입력 → 충돌 검출 → 시간표 이미지 |
| budget 예산·회계 | 국내출장 여비 계산기 | `travel-allowance` | C# | 출장 조건 입력 → 여비 자동 산출 → 내역서 |
| facil 시설·안전 | 비품 QR 라벨 시트 생성기 | `asset-label` | Java | 비품 목록 입력 → A4 QR 라벨 PDF |
| data 데이터 | 표 데이터 통계 요약·차트 생성기 | `data-summarizer` | Python | 표 업로드 → 요약 통계 + 차트 이미지 |
| civil 민원 | 문서 이미지 OCR 추출기 | `doc-ocr` | Python | 이미지 업로드 → 한글 OCR 텍스트 |

7개 업무 분야 상호 중복 없음. 각 서비스는 개인이 접근해 활용하고 끝낼 수 있는 단발 도구입니다.

## 접속 방식 (서브도메인)
플랫폼(http://localhost:5173)은 **로그인 후** 이용합니다(계정은 `auth-service`가 발급하는 JWT로 관리).
목록에서 서비스의 "웹에서 바로 사용"을 누르면 포트가 아니라 **서브도메인**으로 열립니다:
`http://<slug>.localhost` (브라우저가 `*.localhost` 를 127.0.0.1로 처리 → Traefik 리버스 프록시가
Host 헤더로 해당 컨테이너에 라우팅). 각 서비스는 인증 없이 바로 쓰는 단발 도구입니다.

## 공통 규격
독립 실행 · 독립 Dockerfile · 비루트 실행 · `/healthz` · `PORT` 환경변수 ·
통일 오류 응답(`{"error":{"code","message"}}`) · 입력 검증 · `local://examples/<slug>` 배포.

## 배포/검증
- 기본 Seed: `backend/src/main/resources/seed/programs.json` 에 7개 등록(소유자 내부 계정).
- 배포: 플랫폼 docker 모드 `POST /api/programs/{id}/deploy` → `edu-svc-<slug>` 컨테이너 기동 +
  Traefik 라우트 파일 기록 → `http://<slug>.localhost` 접속.
- 각 서비스는 프론트 목록 노출 → "웹에서 바로 사용" 서브도메인 실접속 → 실사용 오류 없음까지 검증합니다.
