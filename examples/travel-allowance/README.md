# 국내출장 여비 계산기 (travel-allowance)

출장 여비를 청구하기 전에 **개인이 한 번에** 금액을 산출하는 단발성 계산기입니다.
조건 입력 → 계산 → 내역서 인쇄/CSV로 끝나며, 저장·공유·결재 기능이 없습니다.

- **언어/기술**: C# (.NET 8 Minimal API)
- **분류(category)**: `budget` 예산·회계
- **접속**: 플랫폼 "웹에서 바로 사용" → `http://travel-allowance.localhost`

## 기능
- 관내/관외 구분 (관내: 4시간 기준 정액)
- 관외: 일비·식비(일수) + 숙박비(박수·지역 상한/실비/무료숙박) + 운임(자가용 km·대중교통 실비·관용차)
- 항목별 내역서 · 합계 · 인쇄 · CSV(엑셀 호환, BOM) 다운로드

표준 예시값(공무원 여비 규정 별표 기준): 일비/식비 25,000원/일, 자가용 262원/km,
숙박 상한 서울 10만·광역시 8만·기타 7만. 실제 기관 규정에 맞게 확인 후 사용하세요.

## API
- `GET /healthz`
- `POST /api/calc` `{tripType,startDate,endDate,region,transport,distanceKm,fareActual,lodgingActual,freeLodging,hours}` → `{items,total,meta}`

## 로컬 실행
```bash
docker build -t travel-allowance .
docker run -p 8080:8080 travel-allowance
curl localhost:8080/healthz
```
