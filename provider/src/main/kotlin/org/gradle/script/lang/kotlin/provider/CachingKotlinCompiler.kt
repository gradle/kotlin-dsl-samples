/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.script.lang.kotlin.provider

import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.CacheKeyBuilder.CacheKeySpec

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.logging.progress.ProgressLoggerFactory

import org.gradle.script.lang.kotlin.KotlinBuildScript
import org.gradle.script.lang.kotlin.resolver.KotlinBuildScriptDependencies
import org.gradle.script.lang.kotlin.cache.ScriptCache

import org.gradle.script.lang.kotlin.support.loggerFor
import org.gradle.script.lang.kotlin.support.ImplicitImports
import org.gradle.script.lang.kotlin.support.KotlinBuildscriptBlock
import org.gradle.script.lang.kotlin.support.KotlinPluginsBlock
import org.gradle.script.lang.kotlin.support.compileKotlinScriptToDirectory
import org.gradle.script.lang.kotlin.support.messageCollectorFor
import org.gradle.script.lang.kotlin.support.CompilerClient

import java.io.File
import kotlin.reflect.KClass
import kotlin.script.dependencies.KotlinScriptExternalDependencies


internal
class CachingKotlinCompiler(
    val scriptCache: ScriptCache,
    val implicitImports: ImplicitImports,
    val compilerClient: CompilerClient,
    val progressLoggerFactory: ProgressLoggerFactory) {

    private
    val logger = loggerFor<KotlinScriptPluginFactory>()

    private
    val cacheKeyPrefix = CacheKeySpec.withPrefix("gradle-script-kotlin")

    private
    val cacheProperties = mapOf("version" to "5")

    fun compileBuildscriptBlockOf(
        scriptPath: String,
        classPath: ClassPath): CompiledScript {

        val scriptDeps = KotlinBuildScriptDependencies(emptyList(), emptyList(), implicitImports.list, null)
        val scriptFileName = scriptFileNameFor(scriptPath)
        return compileScript(cacheKeyPrefix + scriptFileName + "buildscript", classPath) { cacheDir ->
            ScriptCompilationSpec(
                KotlinBuildscriptBlock::class,
                scriptDeps,
                scriptPath,
                File(scriptPath),
                scriptFileName + " buildscript block",
                sourceSections = listOf("buildscript"))
        }
    }

    data class CompiledScript(val location: File, val className: String)

    fun compilePluginsBlockOf(
        scriptPath: String,
        classPath: ClassPath): CompiledPluginsBlock {

        val scriptDeps = KotlinBuildScriptDependencies(emptyList(), emptyList(), implicitImports.list, null)
        val scriptFileName = scriptPath
        val compiledScript = compileScript(cacheKeyPrefix + scriptFileName + "plugins", classPath) {
            ScriptCompilationSpec(
                KotlinPluginsBlock::class,
                scriptDeps,
                scriptPath,
                File(scriptPath),
                scriptFileName + " plugins block",
                sourceSections = listOf("plugins"))}
        return CompiledPluginsBlock(0, compiledScript)
    }

    data class CompiledPluginsBlock(val lineNumber: Int, val compiledScript: CompiledScript)

    fun compileBuildScript(
        scriptPath: String,
        classPath: ClassPath): CompiledScript {

        val scriptFileName = scriptPath
        val scriptDeps = KotlinBuildScriptDependencies(emptyList(), emptyList(), implicitImports.list, null)
        return compileScript(cacheKeyPrefix + scriptFileName, classPath) {
            ScriptCompilationSpec(
                KotlinBuildScript::class,
                scriptDeps,
                scriptPath,
                File(scriptPath),
                scriptFileName)
        }
    }

    private
    fun scriptFileNameFor(scriptPath: String) =
        scriptPath.substringAfterLast(File.separatorChar)

    private
    fun compileScript(
        cacheKeySpec: CacheKeySpec,
        classPath: ClassPath,
        compilationSpecFor: (File) -> ScriptCompilationSpec): CompiledScript {

        val cacheDir = cacheDirFor(cacheKeySpec + classPath) {
            val outputClassFiles =
                compileScriptTo(classesDirOf(baseDir), compilationSpecFor(baseDir), classPath)
            writeClassNameTo(baseDir, outputClassFiles.first().nameWithoutExtension) // TODO: find a way to make it robust
        }
        return CompiledScript(classesDirOf(cacheDir), readClassNameFrom(cacheDir))
    }

    data class ScriptCompilationSpec(
        val scriptTemplate: KClass<out Any>,
        val scriptDependencies: KotlinScriptExternalDependencies?,
        val originalPath: String,
        val scriptFile: File,
        val description: String,
        val additionalSourceFiles: List<File> = emptyList(),
        val sourceSections: List<String> = emptyList())

    private
    fun compileScriptTo(
        outputDir: File,
        spec: ScriptCompilationSpec,
        classPath: ClassPath): List<File> =

        spec.run {
            withProgressLoggingFor(description) {
                logger.debug("Kotlin compilation classpath for {}: {}", description, classPath)
                compilerClient.compileKotlinScriptToDirectory(
                    outputDir,
                    scriptFile,
                    scriptTemplate.qualifiedName!!,
                    scriptDependencies,
                    classPath.asFiles,
                    messageCollectorFor(logger) { path ->
                        if (path == scriptFile.path) originalPath
                        else path
                    },
                    compilerClient.compilerPluginsClasspath,
                    sourceSections.takeIf { it.isNotEmpty() })
            }
        }

    private
    fun cacheDirFor(cacheKeySpec: CacheKeySpec, initializer: PersistentCache.() -> Unit): File =
        scriptCache.cacheDirFor(cacheKeySpec, properties = cacheProperties, initializer = initializer)

    private
    fun writeClassNameTo(cacheDir: File, className: String) =
        scriptClassNameFile(cacheDir).writeText(className)

    private
    fun readClassNameFrom(cacheDir: File) =
        scriptClassNameFile(cacheDir).readText()

    private
    fun scriptClassNameFile(cacheDir: File) = File(cacheDir, "script-class-name")

    private
    fun classesDirOf(cacheDir: File) = File(cacheDir, "classes")

    private
    fun <T> withProgressLoggingFor(description: String, action: () -> T): T {
        val operation = progressLoggerFactory
            .newOperation(this::class.java)
            .start("Compiling script into cache", "Compiling $description into local build cache")
        try {
            return action()
        } finally {
            operation.completed()
        }
    }
}

