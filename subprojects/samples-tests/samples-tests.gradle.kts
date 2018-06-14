import build.*

plugins {
    id("kotlin-library")
}

repositories {
    maven(url = "https://repo.gradle.org/gradle/ext-releases-local")
    maven(url = "https://repo.gradle.org/gradle/libs-releases-local")
}

dependencies {
    compile(project(":test-fixtures"))
    compile("org.xmlunit:xmlunit-matchers:2.5.1")
    implementation("org.gradle:sample-check:0.1.0")
}

val customInstallation by rootProject.tasks
tasks {
    "test" {
        dependsOn(customInstallation)
        inputs.dir("$rootDir/samples")
    }
}

withParallelTests()
