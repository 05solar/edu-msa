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
# 컨테이너 메모리 limit 기준 힙 비율 — JAVA_OPTS 로 환경별 재정의 가능.
# (limit 768Mi 기준 힙 최대 ~576Mi — K8s limits 와 충돌하지 않는 보수적 비율)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# exec 로 java 를 PID 1 로 승격 — SIGTERM 이 JVM 에 직접 전달되어 graceful shutdown 이 동작한다.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
