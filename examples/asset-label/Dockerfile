FROM gradle:8.11-jdk21 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon shadowJar

FROM eclipse-temurin:21-jre
# 한글 PDF 출력을 위한 나눔글꼴
RUN apt-get update && apt-get install -y --no-install-recommends fonts-nanum \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/build/libs/app.jar ./app.jar
RUN useradd -u 1001 -m appuser
USER 1001
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar app.jar"]
