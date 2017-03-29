import build.*

plugins {
    base
}

base {
    archivesBaseName = "gradle-script-kotlin-compiler-plugin"
}

dependencies {
    testCompile(project(":test-fixtures"))

    runtime(kotlin("compiler"))
    runtime(kotlinCompilerPlugin("sam-with-receiver"))
    runtime(kotlinCompilerPlugin("source-sections"))
}

// -- Testing ----------------------------------------------------------
val customInstallation by rootProject.tasks
tasks {
    "test" {
        dependsOn(customInstallation)
    }
}

withParallelTests()

// --- Utility functions -----------------------------------------------
fun kotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:$kotlinVersion"
fun kotlinCompilerPlugin(module: String) = "org.jetbrains.kotlin:kotlin-$module-compiler-plugin:$kotlinVersion"
