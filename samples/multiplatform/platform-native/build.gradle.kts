import org.jetbrains.kotlin.gradle.plugin.KonanArtifactContainer

plugins {
    id("konan")
}

configure<KonanArtifactContainer> {

    library("platform-native") {
        enableMultiplatform(true)
    }

    program("platform-native-test") {
        srcDir("src/test/kotlin")
        commonSourceSets("test")
        libraries {
            artifact("platform-native")
        }
        extraOpts("-tr")
    }
}

dependencies {
    expectedBy(project(":platform-common"))
}

tasks {
    "test"(Task::class) {
        dependsOn("run")
    }
}
