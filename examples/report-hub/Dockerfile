# report-hub (Kotlin/Ktor) — 멀티스테이지(Gradle shadowJar) · 비루트 실행
FROM gradle:8.10-jdk21 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon shadowJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/build/libs/app.jar app.jar
RUN addgroup -S app && adduser -S -u 1001 -G app app
USER app
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar app.jar"]
