import org.gradle.kotlin.dsl.kotlin

plugins {
    base
}

buildscript {
    repositories {
        jcenter()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", "1.2.41"))
        classpath(kotlin("native-gradle-plugin", "0.7"))
    }
}

allprojects {

    group = "multiplatform"
    version = "1.0-SNAPSHOT"

    repositories {
        jcenter()
    }

    tasks {
        withType<Test> {
            testLogging {
                showStandardStreams = true
                events("passed", "failed")
            }
        }
    }

    dependencies {
        subprojects.forEach {
            archives(it)
        }
    }
}

tasks.withType<Wrapper> {
    distributionType = Wrapper.DistributionType.ALL
    gradleVersion = "4.7"
}
