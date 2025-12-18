import shared.*

plugins {
    `kotlin-dsl`
}

buildscript {
    dependencies {
        classpath("composite-build-logic:shared:latest-integration")
    }
}

group = "org.gradle.kotlin.dsl.samples.composite-build-logic"
version = "1.0"

repositories {
    jcenter()
}

dependencies {
    implementation(DependencyVersions.javax.measure.ri.notation)
    implementation("composite-build-logic:shared:latest-integration")
}
