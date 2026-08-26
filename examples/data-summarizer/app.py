import io
import os
from urllib.parse import quote

import pandas as pd
from fastapi import FastAPI, UploadFile, File, Form
from fastapi.responses import HTMLResponse, JSONResponse, StreamingResponse

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager

# 한글 폰트 등록(차트 라벨)
for _p in ["/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
           "/usr/share/fonts/opentype/nanum/NanumGothic.ttf"]:
    if os.path.exists(_p):
        font_manager.fontManager.addfont(_p)
        plt.rcParams["font.family"] = "NanumGothic"
        break
plt.rcParams["axes.unicode_minus"] = False

app = FastAPI(title="data-summarizer")


def err(code, message, status=400):
    return JSONResponse({"error": {"code": code, "message": message}}, status_code=status)


def read_df(name: str, data: bytes) -> pd.DataFrame:
    low = (name or "").lower()
    if low.endswith(".xlsx") or low.endswith(".xlsm") or low.endswith(".xls"):
        return pd.read_excel(io.BytesIO(data))
    # CSV: 인코딩 자동(utf-8-sig → cp949)
    for enc in ("utf-8-sig", "cp949", "latin1"):
        try:
            return pd.read_csv(io.BytesIO(data), encoding=enc)
        except (UnicodeDecodeError, Exception):
            continue
    raise ValueError("CSV 파일을 읽을 수 없습니다.")


def col_type(s: pd.Series) -> str:
    num = pd.to_numeric(s, errors="coerce")
    nonnull = s.notna().sum()
    if nonnull > 0 and num.notna().sum() >= max(1, int(nonnull * 0.8)):
        return "numeric"
    return "categorical"


def r2(x):
    try:
        if pd.isna(x):
            return None
        return round(float(x), 2)
    except Exception:
        return None


def summarize(df: pd.DataFrame):
    cols = []
    for name in df.columns:
        s = df[name]
        t = col_type(s)
        missing = int(s.isna().sum())
        if t == "numeric":
            num = pd.to_numeric(s, errors="coerce").dropna()
            stats = {
                "count": int(num.count()), "missing": missing,
                "mean": r2(num.mean()), "std": r2(num.std()),
                "min": r2(num.min()), "q1": r2(num.quantile(0.25)),
                "median": r2(num.median()), "q3": r2(num.quantile(0.75)),
                "max": r2(num.max()), "sum": r2(num.sum()),
            }
        else:
            vc = s.dropna().astype(str).value_counts()
            stats = {
                "count": int(s.notna().sum()), "missing": missing,
                "unique": int(vc.size),
                "top": [{"value": str(k), "count": int(v)} for k, v in vc.head(5).items()],
            }
        cols.append({"name": str(name), "type": t, "stats": stats})
    return cols


@app.get("/healthz")
def healthz():
    return {"status": "ok", "service": "data-summarizer"}


@app.post("/api/analyze")
async def analyze(file: UploadFile = File(...)):
    data = await file.read()
    if not data:
        return err("VALIDATION", "빈 파일입니다.")
    try:
        df = read_df(file.filename or "", data)
    except Exception as e:
        return err("PARSE", f"파일을 해석할 수 없습니다: {e}")
    if df.empty or len(df.columns) == 0:
        return err("VALIDATION", "표 데이터를 찾을 수 없습니다.")
    if len(df) > 200000:
        df = df.head(200000)
    cols = summarize(df)
    numeric = [c["name"] for c in cols if c["type"] == "numeric"]
    categorical = [c["name"] for c in cols if c["type"] == "categorical"]
    preview = df.head(8).astype(object).where(pd.notna(df.head(8)), None).values.tolist()
    return {
        "rowCount": int(len(df)),
        "colCount": int(len(df.columns)),
        "columns": cols,
        "numeric": numeric,
        "categorical": categorical,
        "headers": [str(c) for c in df.columns],
        "preview": preview,
    }


@app.post("/api/chart")
async def chart(file: UploadFile = File(...), column: str = Form(...), kind: str = Form("auto")):
    data = await file.read()
    try:
        df = read_df(file.filename or "", data)
    except Exception as e:
        return err("PARSE", f"파일을 해석할 수 없습니다: {e}")
    if column not in df.columns:
        return err("VALIDATION", f"'{column}' 열을 찾을 수 없습니다.")

    s = df[column]
    t = col_type(s)
    if kind == "auto":
        kind = "hist" if t == "numeric" else "bar"

    fig, ax = plt.subplots(figsize=(7, 4.2), dpi=110)
    try:
        if kind == "hist" and t == "numeric":
            num = pd.to_numeric(s, errors="coerce").dropna()
            ax.hist(num, bins=min(20, max(5, int(num.size ** 0.5))), color="#2563eb", edgecolor="white")
            ax.set_ylabel("빈도")
            ax.set_title(f"{column} 분포")
        elif kind == "pie":
            vc = s.dropna().astype(str).value_counts().head(8)
            ax.pie(vc.values, labels=[str(i) for i in vc.index], autopct="%1.1f%%", startangle=90,
                   colors=plt.cm.tab20.colors)
            ax.set_title(f"{column} 구성비")
        else:  # bar (categorical value counts, or numeric binned fallback)
            if t == "numeric":
                num = pd.to_numeric(s, errors="coerce").dropna()
                ax.hist(num, bins=min(20, max(5, int(num.size ** 0.5))), color="#2563eb", edgecolor="white")
                ax.set_title(f"{column} 분포")
            else:
                vc = s.dropna().astype(str).value_counts().head(12)
                ax.bar([str(i) for i in vc.index], vc.values, color="#2563eb")
                ax.set_ylabel("건수")
                ax.set_title(f"{column} 빈도")
                plt.setp(ax.get_xticklabels(), rotation=30, ha="right")
        ax.grid(axis="y", color="#e2e8f0")
        for sp in ("top", "right"):
            ax.spines[sp].set_visible(False)
        fig.tight_layout()
        buf = io.BytesIO()
        fig.savefig(buf, format="png")
        buf.seek(0)
    finally:
        plt.close(fig)
    return StreamingResponse(buf, media_type="image/png")


INDEX = open(os.path.join(os.path.dirname(__file__), "index.html"), encoding="utf-8").read()


@app.get("/", response_class=HTMLResponse)
def index():
    return INDEX
