import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("platform.jvm")
}

dependencies {
    expectedBy(project(":platform-common"))
    implementation(kotlin("stdlib-jdk8"))
    testCompile(kotlin("test-testng"))
    testCompile("org.testng:testng:6.14.2")
}

tasks {
    "test"(Test::class) {
        useTestNG()
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            javaParameters = true
            verbose = true
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
    }
}
