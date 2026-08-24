# 멀티스테이지 빌드 — 로컬에 JDK/Gradle 없이 Docker만으로 빌드/실행
FROM gradle:8.10-jdk21 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
# 의존성 캐시 워밍 (소스 변경과 무관하게 캐시 재사용)
RUN gradle --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
RUN gradle --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# 비루트 실행
RUN useradd -r -u 1001 appuser
COPY --from=build /app/build/libs/*.jar app.jar
USER appuser
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar app.jar"]
