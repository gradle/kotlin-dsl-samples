// This script is automatically exposed to downstream consumers
// as the `my-plugin` org.gradle.api.Project plugin

tasks {
    create<Copy>("myCopyTask") {
        group = "sample"
        from("build.gradle.kts")
        into("build/copy")
    }
}
