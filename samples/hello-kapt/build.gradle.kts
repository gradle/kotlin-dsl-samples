import org.gradle.api.internal.HasConvention
import org.gradle.kotlin.dsl.delegateClosureOf
import org.jetbrains.kotlin.gradle.plugin.KaptAnnotationProcessorOptions
import org.jetbrains.kotlin.gradle.plugin.KaptJavacOptionsDelegate
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    application
    idea
    kotlin("jvm") version "1.1.51"
    //Enable kapt plugin
    kotlin("kapt") version "1.1.51"
}

application {
    mainClassName = "samples.HelloWorldKt"
}

repositories {
    jcenter()
}

dependencies {
    compile(kotlin("stdlib"))

    //Use AutoValue to check that annotation processing works
    //https://github.com/google/auto/tree/master/value
    compileOnly("com.google.auto.value:auto-value:1.5")
    //Kapt configuration for an annotation processor
    kapt("com.google.auto.value:auto-value:1.5")
}

kapt {
    // Type safe accessors for Kapt DSL
    correctErrorTypes = true

    // Example of Javac Options configuration
    javacOptions(delegateClosureOf<KaptJavacOptionsDelegate> {
        option("SomeJavacOption", "OptionValue")
    })

    //Kapt Arguments configuration
    arguments(delegateClosureOf<KaptAnnotationProcessorOptions> {
        arg("SomeKaptArgument", "ArgumentValue")
    })
}

// Workaround for https://youtrack.jetbrains.com/issue/KT-17923 and related issues
// Looks like you don't need this if you use Kotlin 1.1.61
val generated = File(buildDir, "generated/source/kapt/main")
java.sourceSets["main"].java.srcDir(generated)
idea.module.generatedSourceDirs.add(generated)