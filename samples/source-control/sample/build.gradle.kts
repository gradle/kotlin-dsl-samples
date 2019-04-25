plugins {
    application
}

application {
    mainClassName = "samples.HelloWorld"
}

dependencies {
    implementation("org.gradle.kotlin.dsl.samples.source-control:compute:latest.integration")
}
