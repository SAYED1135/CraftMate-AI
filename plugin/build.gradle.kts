plugins {
    java
}

group = "ai.craftmate"
version = "0.6.1"
description = "CraftMate AI Connector — Safe Host-Aware Diagnostic & Policy Agent for Paper/Purpur"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17) // Compatible with Java 17 and Java 21 servers (1.20.x - 1.21.x)
}

tasks.jar {
    archiveBaseName.set("CraftMate-Connector")
    archiveVersion.set(project.version.toString())
}
