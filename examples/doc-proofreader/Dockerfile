# build
FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY *.go ./
COPY og.png ./
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /out/app .

# run (distroless, 비루트 uid 65532)
FROM gcr.io/distroless/static-debian12:nonroot
WORKDIR /app
COPY --from=build /out/app /app/app
USER 65532:65532
EXPOSE 8080
ENV PORT=8080
ENTRYPOINT ["/app/app"]
