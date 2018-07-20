import sun.tools.jar.resources.jar

plugins {
    base
    java
    kotlin("jvm") version "1.2.41" apply false
    `maven-publish`
}

allprojects {

    group = "org.gradle.kotlin.dsl.samples.multiproject"

    version = "1.0"

    repositories {
        jcenter()
    }
}

dependencies {
    // Make the root project archives configuration depend on every subproject
    subprojects.forEach {
        archives(it)
    }
}
subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    val sourcesJar by tasks.creating(Jar::class) {
        classifier = "sources"
        from(java.sourceSets["main"].allSource)
    }

    publishing {

        (publications) {
            "mavenJava"(MavenPublication::class) {
                from(components["java"])
                artifact(sourcesJar)
            }
        }
    }
}


