pluginManagement {
    repositories {
        gradlePluginPortal()
        jcenter()
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
        maven("http://kotlin.bintray.com/kotlinx")
        maven("https://dl.bintray.com/jetbrains/kotlin-native-dependencies")
    }
}

rootProject.name = "multiplatform"

include("platform-common", "platform-jvm", "platform-js", "platform-native")
