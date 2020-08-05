import org.gradle.api.internal.file.pattern.PatternMatcherFactory.compile
import org.gradle.kotlin.dsl.kotlin

plugins {
    id("com.android.application") version "3.0.1"
    kotlin("android") version "1.1.61"
    kotlin("kapt") version "1.1.61"
}

android {
    buildToolsVersion("27.0.1")
    compileSdkVersion(27)

    defaultConfig {
        minSdkVersion(15)
        targetSdkVersion(27)

        applicationId = "com.example.kotlingradle.kapt"
        versionCode = 1
        versionName = "1.0"
    }

    // Enable databinding library for this project
    dataBinding.isEnabled = true
}

dependencies {
    val supportLibraryVersion = "27.0.1"
    implementation("com.android.support:appcompat-v7:$supportLibraryVersion")
    implementation(kotlin("stdlib", "1.1.61"))

    //Dagger 2 dependencies
    implementation("com.google.dagger:dagger:2.13")
    kapt("com.google.dagger:dagger-compiler:2.13")

    // Version of databinding compiler should be equal to version of android plugin
    // Use kapt to run databinding annotation processor
    kapt("com.android.databinding:compiler:3.0.1")

    //com.android.databinding:library:1.3.1 depends on support-v4 (this dependency added implictly by Android plugi,
    //to prevent dependency version conflict set v4 version explicitly
    implementation("com.android.support:support-v4:$supportLibraryVersion")
}

repositories {
    jcenter()
    google()
}
