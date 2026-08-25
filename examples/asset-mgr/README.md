# asset-mgr · 교육 기자재·자산 관리 (C#/.NET minimal API)

## 대상 / 문제
학교의 전산기기·실험기자재·가구·체육용품 등 자산은 등록(취득)부터 배치·이관·수리·폐기까지
생애주기를 거친다. 이 서비스는 그 생애주기를 상태 전이로 관리하고, 감가상각 현재가치와
재물조사(실사) 이력을 제공한다.

## 핵심 업무 흐름 / 상태
`IN_STORAGE`(보관) → `IN_USE`(사용중, 배치) ↔ `UNDER_REPAIR`(수리중) → 완료 후 복귀
· `DISPOSED`(폐기) · `LOST`(분실). 이관(사용중 재배치), 재물조사(미확인 시 분실 처리).

## 감가상각 / 자산번호
- `currentValue` = 정액법(취득가 × max(0, 1 − 경과연수/내용연수)), 폐기·분실은 0.
- 자산번호 `EDU-YYYY-00001` 자동 채번.

## 데이터 모델
Asset: id, assetNo, name, category(전산기기/실험기자재/도서/가구/체육용품/기타), acquiredDate,
acquiredCost, usefulLifeYears, location, custodian, status, currentValue, repairCostTotal,
lastAuditDate, notes, history[], audits[]

## 주요 API
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/healthz` | 상태 |
| GET | `/api/assets?status=&category=&location=&custodian=&q=&sort=&page=&size=` | 목록·필터·검색·정렬·페이지 |
| POST | `/api/assets` | 등록(취득) |
| GET | `/api/assets/{id}` | 상세(currentValue 포함) |
| PATCH | `/api/assets/{id}` | 수정(폐기 제외) |
| POST | `/api/assets/{id}/assign` | 배치(location·custodian → 사용중) |
| POST | `/api/assets/{id}/transfer` | 이관(사용중 재배치) |
| POST | `/api/assets/{id}/repair` | 수리 접수(reason) |
| POST | `/api/assets/{id}/repair-done` | 수리 완료(cost 누적) |
| POST | `/api/assets/{id}/dispose` | 폐기(reason) |
| POST | `/api/assets/{id}/report-lost` | 분실 신고(reason) |
| POST | `/api/assets/{id}/recover` | 회수(재발견, 분실 자산 복구) |
| POST | `/api/assets/{id}/audit` | 재물조사(auditor, found) |
| GET | `/api/assets/export` | 재물조사용 CSV 내보내기 |
| GET | `/api/assets/{id}/history` | 이력 |
| GET | `/api/stats` | 상태/분류 통계, 취득가·현재가치 합계, 수리중 수 |

물품대장 필드: quantity(수량), model(규격/모델), acquisitionMethod(취득방법), supplier(구입처), budgetAccount(예산과목).
감가상각은 0~1 상한, 비망가액 1,000원 유지. 내용연수 미지정 시 분류별 표준값(전산5·실험8·도서10·가구9·체육6) 적용.

## 검증 규칙 / 예외
- name 필수(120자), category 화이트리스트, acquiredCost≥0, usefulLifeYears>0, acquiredDate 형식(YYYY-MM-DD).
- 전이 통제: 배치(보관/사용중), 이관(사용중), 수리(보관/사용중), 수리완료(수리중), 폐기(폐기 제외), 분실(폐기/분실 제외).
- 재물조사 미확인(found=false) 시 보관/사용중 자산은 분실 처리. 잘못된 전이 409, 미존재 404, 입력오류 400.
- 오류 형식 `{"error":{"code","message"}}`.

## 영속성 / 운영 경계
`DATA_FILE` 지정 시 JSON 보존(**실서버는 볼륨/emptyDir로 DATA_FILE 마운트 필수** — 미지정 시 재기동마다 시드 초기화). actor는 본문 값(플랫폼 게이트웨이 신원 주입 전제). KST. 비루트(.NET8 내장 app 사용자).
배포 경로 `/svc/asset-mgr`. 향후: 바코드/QR 라벨·출력, 정기 재물조사 스케줄·미실사 알림, 감가상각 방식/잔존가치 옵션, 예산·회계 연동, 수량 부분 폐기 추적.

## 실행
```bash
docker build -t asset-mgr . && docker run -p 8080:8080 asset-mgr
curl localhost:8080/healthz
curl -X POST localhost:8080/api/assets -d '{"name":"노트북","category":"전산기기","acquiredCost":1200000,"usefulLifeYears":5}'
```
