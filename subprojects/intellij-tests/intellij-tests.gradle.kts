import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij") version "0.3.7"
    id("kotlin-library")
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

intellij {
    version = "IC-2018.2.2"
    setPlugins(
        "Kotlin",
        "Groovy",
        "Properties",
        "Gradle"
    )
}

dependencies {
    testCompile("junit:junit:4.12")
}
