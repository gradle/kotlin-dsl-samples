plugins {
    id("org.jetbrains.intellij") version "0.3.4"
    id("kotlin-library")
}

intellij {
    setPlugins("Kotlin", "Gradle")
}

dependencies {
    testCompile("junit:junit:4.12")
}
