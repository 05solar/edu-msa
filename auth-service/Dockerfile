# 멀티스테이지 빌드 — 로컬에 JDK/Gradle 없이 Docker만으로 빌드/실행
FROM gradle:8.10-jdk21 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
RUN gradle --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
RUN gradle --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# 인증 서비스는 외부 도구가 필요 없으므로 비특권 사용자로 실행한다.
RUN useradd -r -u 10001 -m appuser
USER appuser
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar app.jar"]
