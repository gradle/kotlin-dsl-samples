import com.gradle.publish.PluginBundleExtension

buildscript {
    repositories { gradleScriptKotlin() }
    dependencies { classpath(kotlinModule("gradle-plugin")) }
}

plugins {
    id("com.gradle.plugin-publish") version "0.9.6"
}

apply {
    plugin("kotlin")
}

repositories {
    jcenter()
}

dependencies {
    compile(gradleApi())
}

version = "0.1.0"

group = "org.gradle.script.lang.kotlin"

val pluginPortalIndexFile = file("build/resources/plugin-portal-index.jsonl")

val fetchPPI = task<codegen.FetchPluginPortalIndex>("fetchPluginPortalIndex") {
    outputFile = pluginPortalIndexFile
    onlyIf { !pluginPortalIndexFile.exists() }
}

val generatePPE = task<codegen.GeneratePluginPortalExtensions>("generatePluginPortalExtensions") {
    dependsOn(fetchPPI)
    jsonlIndexFile = pluginPortalIndexFile
}

tasks.getByName("compileKotlin").dependsOn(generatePPE)

configure<PluginBundleExtension> {
    website = "https://www.gradle.org/"
    vcsUrl = "https://github.com/gradle/gradle-script-kotlin"
    description = "Type-safe Plugin Portal index."
    tags = listOf("gradle", "kotlin")
    plugins.create("ppi") {
        it.id = "org.gradle.script.lang.kotlin.ppi"
        it.displayName = "Type-safe Plugin Portal index"
    }
}
