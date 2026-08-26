import io
import os

from fastapi import FastAPI, UploadFile, File, Form
from fastapi.responses import HTMLResponse, JSONResponse, FileResponse
from PIL import Image, ImageOps
import pytesseract

app = FastAPI(title="doc-ocr")

ALLOWED_LANGS = {"kor+eng", "kor", "eng"}
MAX_SIDE = 3000  # 너무 큰 이미지는 축소


def err(code, message, status=400):
    return JSONResponse({"error": {"code": code, "message": message}}, status_code=status)


@app.get("/healthz")
def healthz():
    return {"status": "ok", "service": "doc-ocr"}


def preprocess(img: Image.Image) -> Image.Image:
    img = ImageOps.exif_transpose(img)
    img = img.convert("L")            # 흑백
    img = ImageOps.autocontrast(img)  # 대비 보정
    w, h = img.size
    if max(w, h) > MAX_SIDE:
        scale = MAX_SIDE / max(w, h)
        img = img.resize((int(w * scale), int(h * scale)))
    return img


@app.post("/api/ocr")
async def ocr(file: UploadFile = File(...), lang: str = Form("kor+eng")):
    if lang not in ALLOWED_LANGS:
        lang = "kor+eng"
    data = await file.read()
    if not data:
        return err("VALIDATION", "빈 파일입니다.")
    try:
        img = Image.open(io.BytesIO(data))
        img.load()
    except Exception:
        return err("VALIDATION", "이미지 파일만 업로드할 수 있습니다(PNG·JPG 등).")
    try:
        proc = preprocess(img)
        text = pytesseract.image_to_string(proc, lang=lang)
    except pytesseract.TesseractError as e:
        return err("OCR", f"문자 인식 중 오류가 발생했습니다: {e}")

    text = text.strip()
    words = len([w for w in text.split() if w])
    lines = len([ln for ln in text.splitlines() if ln.strip()])
    return {
        "text": text,
        "chars": len(text),
        "words": words,
        "lines": lines,
        "lang": lang,
    }


INDEX = open(os.path.join(os.path.dirname(__file__), "index.html"), encoding="utf-8").read()


@app.get("/og.png")
def og():
    return FileResponse(os.path.join(os.path.dirname(__file__), "og.png"), media_type="image/png")


@app.get("/", response_class=HTMLResponse)
def index():
    return INDEX
