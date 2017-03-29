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

package org.gradle.script.lang.kotlin.support

import org.gradle.api.HasImplicitReceiver
import org.gradle.internal.classpath.ClassPath

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

import org.jetbrains.kotlin.daemon.client.KotlinCompilerClient
import org.jetbrains.kotlin.daemon.common.*

import org.slf4j.Logger

import java.io.File
import kotlin.script.dependencies.KotlinScriptExternalDependencies


internal const val SAM_WITH_RECEIVER_JAR_PREFIX = "kotlin-sam-with-receiver"
private const val SAM_WITH_RECEIVER_ID = "org.jetbrains.kotlin.samWithReceiver"
internal const val SOURCE_SECTIONS_JAR_PREFIX = "kotlin-source-sections"
private const val SOURCE_SECTIONS_ID = "org.jetbrains.kotlin.sourceSections"

private val SAM_WITH_RECEIVER_ANNOTATIONS = listOf(HasImplicitReceiver::class.qualifiedName!!)

internal
fun CompilerClient.compileKotlinScriptToDirectory(
    outputDirectory: File,
    scriptFile: File,
    scriptTemplateName: String,
    scriptDeps: KotlinScriptExternalDependencies?,
    classPath: List<File>,
    messageCollector: MessageCollector,
    compilerPluginsClasspath: ClassPath,
    compileOnlySections: Collection<String>? = null): List<File> {

    val samWithReceiverPlugin = compilerPluginsClasspath.filter { it.name.startsWith(SAM_WITH_RECEIVER_JAR_PREFIX) }.asFiles
    if (samWithReceiverPlugin.isEmpty())
        throw Exception("$SAM_WITH_RECEIVER_JAR_PREFIX is not found in the classpath $compilerPluginsClasspath")
    val args = arrayListOf(
        "-d", outputDirectory.canonicalPath,
        "-cp", classPath.joinToString(File.pathSeparator) { it.canonicalPath },
        "-script-templates", scriptTemplateName,
        "-Xreport-output-files",
        "-Xplugin=${samWithReceiverPlugin.first().canonicalPath}",
        "-P", SAM_WITH_RECEIVER_ANNOTATIONS.joinToString(",") { "plugin:$SAM_WITH_RECEIVER_ID:annotation=$it" }
    ).apply {
        if (compileOnlySections != null && compileOnlySections.isNotEmpty()) {
            val sourceSectionsPlugin = compilerPluginsClasspath.filter { it.name.startsWith(SOURCE_SECTIONS_JAR_PREFIX) }.asFiles
            if (sourceSectionsPlugin.isEmpty())
                throw Exception("$SOURCE_SECTIONS_JAR_PREFIX is not found in the classpath $sourceSectionsPlugin")
            add("-Xplugin=${sourceSectionsPlugin.first().canonicalPath}")
            add("-P")
            add(compileOnlySections.joinToString(",") { "plugin:$SOURCE_SECTIONS_ID:allowedSection=$it" })
        }
        if (scriptDeps != null) {
            // TODO: check the escaping
            val cp = scriptDeps.classpath.joinToString(File.pathSeparator)
            val imp = scriptDeps.imports.joinToString(";")
            add("-Xscript-resolver-environment=classpath=\"$cp\",imports=\"$imp\"")
        }
        add(scriptFile.canonicalPath)
    }
//    messageCollector.report(CompilerMessageSeverity.INFO, "compiling with args:\n${args.joinToString("\n")}")
    return compileImpl(messageCollector, *args.toTypedArray())
}

private fun CompilerClient.compileImpl(messageCollector: MessageCollector, vararg args: String): List<File> {
    val results = arrayListOf<File>()
    val code = KotlinCompilerClient.compile(daemon, sessionId, CompileService.TargetPlatform.JVM, args, messageCollector,
        { classfile, sources -> results.add(classfile) },
        reportSeverity = ReportSeverity.DEBUG)

    if (code != 0) throw IllegalStateException("Internal error: unable to compile script (error code: $code), see log for details")

    return results
}


internal
fun CompilerClient.compileToJar(
    outputJar: File,
    sourceFiles: Iterable<File>,
    logger: Logger,
    classPath: Iterable<File> = emptyList()): Boolean =

    compileTo(outputJar, sourceFiles, logger, classPath)


internal
fun CompilerClient.compileToDirectory(
    outputDirectory: File,
    sourceFiles: Iterable<File>,
    logger: Logger,
    classPath: Iterable<File> = emptyList()): Boolean =

    compileTo(outputDirectory, sourceFiles, logger, classPath)


private
fun CompilerClient.compileTo(
    output: File,
    sourceFiles: Iterable<File>,
    logger: Logger,
    classPath: Iterable<File>): Boolean {

        withMessageCollectorFor(logger) { messageCollector ->

            compileImpl(messageCollector,
                "-d", output.canonicalPath,
                "-cp", classPath.joinToString(File.pathSeparator) { it.canonicalPath } + File.pathSeparator + kotlinStdlibJar.canonicalPath,
                *sourceFiles.map { it.path }.toTypedArray()
            )
            return true
        }
}


private
val kotlinStdlibJar: File
    get() = File(Unit::class.java.let {


it.classLoader.getResource(it.name.replace('.', '/') + ".class").toString() // TODO: check if it is a good enough replacement for the old code relying on the intellij utils
})


private inline
fun <T> withMessageCollectorFor(log: Logger, action: (MessageCollector) -> T): T {
    val messageCollector = messageCollectorFor(log)
    return action(messageCollector)
}

internal
fun messageCollectorFor(log: Logger, pathTranslation: (String) -> String = { it }): MessageCollector =

    object : MessageCollector {

        var errors = 0

        override fun hasErrors() = errors > 0

        override fun clear() { errors = 0 }

        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {

            fun msg() =
                location?.run {
                    path?.let(pathTranslation)?.let { path ->
                        when {
                            line >= 0 && column >= 0 -> compilerMessageFor(path, line, column, message)
                            else -> "$path: $message"
                        }
                    }
                } ?: message

            fun taggedMsg() =
                "${severity.presentableName[0]}: ${msg()}"

            when (severity) {
                in CompilerMessageSeverity.ERRORS -> {
                    errors++
                    log.error { taggedMsg() }
                }
                in CompilerMessageSeverity.VERBOSE -> log.debug { msg() }
                CompilerMessageSeverity.STRONG_WARNING -> log.info { taggedMsg() }
                CompilerMessageSeverity.WARNING -> log.info { taggedMsg() }
                CompilerMessageSeverity.INFO -> log.info { msg() }
                else -> log.debug { taggedMsg() }
            }
        }
    }


internal
fun compilerMessageFor(path: String, line: Int, column: Int, message: String) =
    "$path:$line:$column: $message"
