# 표준 규격 예제 — 파이썬 표준 라이브러리만 사용 (빠른 빌드)
FROM python:3.11-slim
WORKDIR /app
COPY . .
# 비루트 실행
RUN useradd -r -u 1001 appuser
USER appuser
ENV PORT=8080
EXPOSE 8080
CMD ["python", "main.py"]
