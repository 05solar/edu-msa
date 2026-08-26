FROM python:3.12-slim

# 차트 한글 라벨용 나눔글꼴
RUN apt-get update && apt-get install -y --no-install-recommends fonts-nanum \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py index.html ./

RUN useradd -u 1001 -m appuser
USER 1001

EXPOSE 8080
ENV PORT=8080 MPLCONFIGDIR=/tmp/mpl
CMD ["sh", "-c", "uvicorn app:app --host 0.0.0.0 --port ${PORT:-8080}"]
