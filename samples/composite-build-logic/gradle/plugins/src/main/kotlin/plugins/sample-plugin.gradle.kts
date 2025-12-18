package plugins

import shared.*

plugins {
    `java-library`
}

dependencies {
    "api"(DependencyVersions.javax.measure.api.notation)
}

repositories {
    jcenter()
}

tasks.register<SampleTask>("sample") {
    message.set("SampleTask executed!")
}
