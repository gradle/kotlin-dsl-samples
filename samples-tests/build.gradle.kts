import build.withParallelTests
import build.withTestWorkersMemoryLimits

apply<plugins.KotlinLibrary>()

dependencies {
    val compile by configurations
    compile(project(":test-fixtures"))
    compile("org.xmlunit:xmlunit-matchers:2.4.0")
}

val customInstallation by rootProject.tasks
tasks {
    "test" {
        dependsOn(customInstallation)
        inputs.dir("../samples")
    }
}

withTestWorkersMemoryLimits()
withParallelTests()
