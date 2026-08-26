plugins {
    application
    id("com.gradleup.shadow") version "8.3.5"
}

repositories { mavenCentral() }

dependencies {
    implementation("org.apache.pdfbox:pdfbox:2.0.31")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

application { mainClass.set("App") }

tasks.shadowJar {
    archiveBaseName.set("app")
    archiveClassifier.set("")
    archiveVersion.set("")
}
