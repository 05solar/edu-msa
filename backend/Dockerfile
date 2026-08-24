# 멀티스테이지 빌드 — 로컬에 JDK/Gradle 없이 Docker만으로 빌드/실행
FROM gradle:8.10-jdk21 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
RUN gradle --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
RUN gradle --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# 배포 파이프라인 실행 도구: git(clone) · docker CLI(build/run) · kubectl(real 모드 apply)
RUN apt-get update \
 && apt-get install -y --no-install-recommends git curl ca-certificates docker.io \
 && curl -fsSL https://dl.k8s.io/release/v1.30.0/bin/linux/amd64/kubectl -o /usr/local/bin/kubectl \
    && chmod +x /usr/local/bin/kubectl || echo "kubectl download skipped" \
 && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/build/libs/*.jar app.jar
ENV PORT=8080
EXPOSE 8080
# 로컬 Docker 배포 모드에서 호스트 docker.sock 접근을 위해 root로 실행(개발용).
# 실제 K8s(real 모드)에서는 ServiceAccount 권한(deploy/k8s/rbac.yaml)을 사용한다.
ENTRYPOINT ["sh", "-c", "java -jar app.jar"]
