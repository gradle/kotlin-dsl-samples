import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.spring.boot"
version = "1.0.0-SNAPSHOT"

plugins {
    val kotlinVersion = "1.2.40"
    id("org.springframework.boot") version "2.0.1.RELEASE"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    id("io.spring.dependency-management") version "1.0.5.RELEASE"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

(tasks.findByName("test") as Test).useJUnitPlatform()

repositories {
    mavenCentral()
}

dependencies {
    compile("org.springframework.boot:spring-boot-starter-web")
    compile(kotlin("stdlib-jdk8"))
    compile(kotlin("reflect"))
    compile("com.fasterxml.jackson.module:jackson-module-kotlin")
    testCompile("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "junit")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
