# 문서 이미지 OCR 추출기 (doc-ocr)

종이 민원서류·안내문을 **개인이 한 번에** 디지털 텍스트로 옮기는 단발성 도구입니다.
이미지 업로드 → 인식 → 복사/다운로드로 끝나며, 저장·공유 기능이 없습니다.

- **언어/기술**: Python (FastAPI, Tesseract OCR, Pillow)
- **분류(category)**: `civil` 민원
- **접속**: 플랫폼 "웹에서 바로 사용" → `http://doc-ocr.localhost`

## 기능
- 이미지 업로드(드래그&드롭, PNG·JPG·TIFF·BMP)
- 한글/영문 OCR(언어 선택), 전처리(흑백·대비 보정·과대이미지 축소)
- 편집 가능한 결과 텍스트 · 글자/단어/줄 수 · 복사 · .txt 다운로드

## API
- `GET /healthz`
- `POST /api/ocr` (multipart file + lang: kor+eng|kor|eng) → `{text,chars,words,lines,lang}`

## 로컬 실행
```bash
docker build -t doc-ocr .
docker run -p 8080:8080 doc-ocr
curl localhost:8080/healthz
```
