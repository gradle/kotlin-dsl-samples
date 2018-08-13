import shared.*

plugins {
    id("plugins.sample-plugin")
}

tasks {
    sample {
        message.set("Hello included build logic!")
    }
}

dependencies {
    implementation(DependencyVersions.javax.measure.ri.notation)
}
