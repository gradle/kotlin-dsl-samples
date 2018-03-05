import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.spring.boot"
version = "1.0.0-SNAPSHOT"

plugins {
    val kotlinVersion = "1.2.21"
    id("org.springframework.boot") version "2.0.0.RELEASE"
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion
    id("io.spring.dependency-management") version "1.0.4.RELEASE"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

val test by tasks.getting(Test::class) {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
}

dependencies {
    compile("org.springframework.boot:spring-boot-starter-web")
    compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    compile("org.jetbrains.kotlin:kotlin-reflect")
    compile("com.fasterxml.jackson.module:jackson-module-kotlin")
    testCompile("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "junit")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
