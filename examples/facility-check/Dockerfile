FROM python:3.11-slim
WORKDIR /app
COPY . .
RUN useradd -r -u 1001 appuser
USER appuser
ENV PORT=8080
EXPOSE 8080
CMD ["python", "main.py"]
