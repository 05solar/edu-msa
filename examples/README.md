# 예제 서비스 모음 (업무 분야별 실동작 프로그램)

표준 규격([../docs/MSA_SERVICE_SPEC.md](../docs/MSA_SERVICE_SPEC.md))을 지켜 **바로 빌드·실행**되는
예제입니다. 각 폴더는 그대로 GitHub 레포에 올려 플랫폼에 등록할 수 있습니다.
모두 파이썬 표준 라이브러리만 사용해 의존성 설치 없이 빠르게 빌드됩니다.

| 폴더 | 이름 | 업무 분야 | 하는 일 |
| --- | --- | --- | --- |
| `sample-service/` | 출장 정산 자동 계산기 | budget | 표준 규격 최소 예제 (참고용) |
| `doc-formatter/` | 기안문 서식 생성기 | doc(문서·공문) | 항목 입력 → 표준 기안문 서식 텍스트 |
| `score-stats/` | 성적 통계 계산기 | student(학생·성적) | 점수 → 평균·표준편차·등급 분포 |
| `class-hours/` | 교육과정 시수 계산기 | curri(교육과정) | 주당 시수·주수 → 학기·연간 시수 |
| `budget-rate/` | 예산 집행률 계산기 | budget(예산·회계) | 예산·집행액 → 집행률·잔액·상태 |
| `facility-check/` | 시설 점검 체크리스트 | facil(시설·안전) | 항목 체크 → 완료율·미점검 목록 |
| `data-summary/` | 데이터 요약 통계 | data(데이터) | 숫자 붙여넣기 → 개수·합계·평균·중앙값 |
| `civil-reply/` | 민원 답변 초안 생성기 | civil(민원) | 유형·내용 → 정중한 답변 초안 |

## 공통 실행 방법

```bash
cd <폴더>
docker build -t <slug> .
docker run -e PORT=8080 -p 8080:8080 <slug>
# 브라우저 http://localhost:8080  ·  http://localhost:8080/healthz → ok
```

## 플랫폼에 등록

각 폴더를 GitHub 공개 레포로 올린 뒤, 플랫폼 "프로그램 등록"에서 레포 주소를 입력하고
규격 검증 → 등록 요청하면 새 서비스로 배포됩니다.
