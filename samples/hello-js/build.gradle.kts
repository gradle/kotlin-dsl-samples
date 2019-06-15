plugins {
    id("kotlin2js") version "1.3.21"
    id("com.craigburke.karma") version "1.4.4"
}

dependencies {
    compile(kotlin("stdlib-js"))
    testCompile(kotlin("test-js"))
}

repositories {
    jcenter()
}

tasks {
    compileKotlin2Js {
        kotlinOptions {
            outputFile = "${sourceSets.main.get().output.resourcesDir}/output.js"
            sourceMap = true
        }
    }
    val unpackKotlinJsStdlib by registering {
        group = "build"
        description = "Unpack the Kotlin JavaScript standard library"
        val outputDir = file("$buildDir/$name")
        inputs.property("compileClasspath", configurations.compileClasspath.get())
        outputs.dir(outputDir)
        doLast {
            val kotlinStdLibJar = configurations.compileClasspath.get().single {
                it.name.matches(Regex("kotlin-stdlib-js-.+\\.jar"))
            }
            copy {
                includeEmptyDirs = false
                from(zipTree(kotlinStdLibJar))
                into(outputDir)
                include("**/*.js")
                exclude("META-INF/**")
            }
        }
    }
    val assembleWeb by registering(Copy::class) {
        group = "build"
        description = "Assemble the web application"
        includeEmptyDirs = false
        from(unpackKotlinJsStdlib)
        from(sourceSets.main.get().output) {
            exclude("**/*.kjsm")
        }
        into("$buildDir/web")
    }

    val populateNodeModules by registering {
        group = "build"
        description = "Populate Node modules directory for karma"
        val outputDir = file("$buildDir/$name")
        inputs.property("testCompileClasspath", configurations.testCompileClasspath.get())
        outputs.dir(outputDir)
        doLast {
            val fromJars = configurations.testCompileClasspath.get()
                .filter { it.name.matches(Regex(".*\\.jar")) }
                .map { zipTree(it) }
            copy {
                includeEmptyDirs = false
                from(fromJars)
                into(outputDir)
                include("**/*.js")
                exclude("META-INF/**")
            }
        }
    }

    node {
        version = "10.16.0"
    }

    karma {
        dependencies(listOf("mocha"))
        frameworks = listOf("mocha")
        browsers = listOf("PhantomJS")
        files = listOf(
            "$buildDir/${populateNodeModules.name}/kotlin.js",
            "$buildDir/${populateNodeModules.name}/*.js",
            "${compileKotlin2Js.get().outputFile}",
            "${compileTestKotlin2Js.get().outputFile}"
        )
    }

    karmaRun {
        dependsOn(compileTestKotlin2Js, populateNodeModules)
    }
    test { dependsOn(karmaRun) }
    clean { dependsOn(karmaClean) }

    assemble {
        dependsOn(assembleWeb)
    }
}
