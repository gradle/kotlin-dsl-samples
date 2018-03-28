import com.moowork.gradle.node.npm.NpmTask
import com.moowork.gradle.node.task.NodeTask
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

plugins {
    id("kotlin-platform-js")
    id("com.moowork.node") version "1.2.0"
}

dependencies {
    "expectedBy"(project(":platform-common"))
    "implementation"(kotlin("stdlib-js"))
    "testCompile"(kotlin("test-js"))
}

tasks {

    node {
        version = "9.9.0"
        download = true
    }

    "compileKotlin2Js"(Kotlin2JsCompile::class) {
        kotlinOptions {
            moduleKind = "umd"
            sourceMap = true
            sourceMapEmbedSources = "always"
        }
    }

    "compileTestKotlin2Js"(Kotlin2JsCompile::class) {
        kotlinOptions {
            moduleKind = "umd"
            sourceMap = true
            sourceMapEmbedSources = "always"
        }
    }

    "populateNodeModules"(Copy::class) {
        dependsOn("compileKotlin2Js")
        val kotlin2JsCompile = tasks["compileKotlin2Js"] as Kotlin2JsCompile
        from(kotlin2JsCompile.destinationDir)
        configurations.testCompile.forEach {
            from(zipTree(it.absolutePath).matching { include("*.js") })
        }
        into("$buildDir/node_modules")
    }

    "installQunit"(NpmTask::class) {
        inputs.property("qunitVersion", "2.6.0")
        outputs.dir(file("node_modules/qunit"))
        setArgs(listOf("install", "qunit@2.6.0"))
    }

    "runQunit"(NodeTask::class) {
        dependsOn("compileTestKotlin2Js", "populateNodeModules", "installQunit")
        setScript(file("node_modules/qunit/bin/qunit"))
        val kotlin2JsCompile = tasks["compileTestKotlin2Js"] as Kotlin2JsCompile
        setArgs(listOf(projectDir.toPath().relativize(file(kotlin2JsCompile.outputFile).toPath())))
    }

    "test"(Task::class) {
        dependsOn("runQunit")
    }
}
