# safety-check (Rust/axum) — 멀티스테이지 · 비루트 실행
FROM rust:1.82-slim AS build
WORKDIR /src
COPY Cargo.toml ./
COPY src ./src
RUN cargo build --release

FROM debian:bookworm-slim
WORKDIR /app
COPY --from=build /src/target/release/safety-check /app/safety-check
RUN useradd -r -u 1001 app
USER app
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/safety-check"]
