plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.edu.auth"
version = "0.1.0"

// Boot 3.3 BOM 의 Testcontainers(1.19.x)는 Docker Engine 29+ 의 최소 API 버전을 협상하지
// 못한다(400 Bad Request) — API 버전 협상이 되는 docker-java 를 쓰는 버전으로 올린다.
extra["testcontainers.version"] = "1.21.3"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")   // /actuator/prometheus 노출
    // 분산 rate limit 카운터(Redis) — 장애 시 인메모리 폴백(FailoverAttemptStore)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")

    // 스키마 이력 관리 — ddl-auto:update 대체 (운영은 validate + Flyway 마이그레이션)
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-mysql")   // MariaDB/MySQL 지원 모듈

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // 실 DB 검증(Flyway MariaDB 동판 + validate) — H2 로는 재현 불가
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mariadb")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
