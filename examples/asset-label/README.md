# 비품 QR 라벨 시트 생성기 (asset-label)

비품에 라벨을 붙일 때 **개인이 한 번에** 라벨 시트를 만드는 단발성 도구입니다.
목록 입력 → PDF 생성 → 인쇄로 끝나며, 저장·공유·대장관리 기능이 없습니다.

- **언어/기술**: Java 21 (com.sun.net.httpserver, PDFBox, ZXing)
- **분류(category)**: `facil` 시설·안전
- **접속**: 플랫폼 "웹에서 바로 사용" → `http://asset-label.localhost`

## 기능
- 비품 목록 입력(관리번호,품명) — 엑셀 두 열 복사·붙여넣기 지원
- 각 품목 QR 코드 생성(내용: 관리번호+품명 또는 관리번호만)
- A4 라벨 시트 PDF(가로·세로 칸수 지정, 여러 장 자동 분할) · 미리보기 · 다운로드
- 한글 라벨 출력(나눔글꼴 임베드)

## API
- `GET /healthz`
- `POST /api/labels` `{assets:[{code,name}],cols,rows,qrContent,title}` → `application/pdf`

## 로컬 실행
```bash
docker build -t asset-label .
docker run -p 8080:8080 asset-label
curl localhost:8080/healthz
```
