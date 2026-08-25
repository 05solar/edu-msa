plugins {
    application
    id("com.gradleup.shadow") version "8.3.5"
}

repositories { mavenCentral() }

dependencies {
    implementation("io.javalin:javalin:6.3.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-simple:2.0.13")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

application { mainClass.set("App") }

tasks.shadowJar { archiveFileName.set("app.jar") }
