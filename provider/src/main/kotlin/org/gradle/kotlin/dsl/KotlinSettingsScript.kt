/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.kotlin.dsl

import org.gradle.api.Script
import org.gradle.api.initialization.Settings

import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import org.gradle.kotlin.dsl.support.serviceOf

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.PathValidation
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileTree
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.logging.LoggingManager
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.provider.PropertyState
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.gradle.api.tasks.WorkResult
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec

import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.file.DefaultFileOperations
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.StreamHasher
import org.gradle.internal.reflect.Instantiator

import java.io.File
import java.net.URI
import java.util.concurrent.Callable

import kotlin.script.extensions.SamWithReceiverAnnotations
import kotlin.script.templates.ScriptTemplateDefinition


/**
 * Base class for Kotlin settings scripts.
 */
@ScriptTemplateDefinition(
    resolver = KotlinBuildScriptDependenciesResolver::class,
    scriptFilePattern = "settings\\.gradle\\.kts")
@SamWithReceiverAnnotations("org.gradle.api.HasImplicitReceiver")
abstract class KotlinSettingsScript(settings: Settings) : Settings by settings, Script by scriptApiFor(settings) {

    /**
     * Configures the build script classpath for settings.
     *
     * @see [Settings.buildscript]
     */
    @Suppress("unused")
    open fun buildscript(@Suppress("unused_parameter") block: ScriptHandlerScope.() -> Unit) = Unit

    inline
    fun apply(crossinline configuration: ObjectConfigurationAction.() -> Unit) =
        settings.apply({ it.configuration() })


    // ---- Resolve multiple inheritance via delegation conflicts

    /**
     * Returns the build script handler for settings. You can use this handler to query details about the build
     * script for settings, and manage the classpath used to compile and execute the settings script.
     *
     * @return the classpath handler. Never returns null.
     *
     * @since 4.4
     */
    @Incubating
    override fun getBuildscript(): ScriptHandler = settings.buildscript

    /**
     * Applies zero or more plugins or scripts.
     *
     * The given closure is used to configure an [ObjectConfigurationAction], which “builds” the plugin application.
     *
     * @param closure the closure to configure an [ObjectConfigurationAction] with before “executing” it
     */
    override fun apply(closure: Closure<*>) = settings.apply(closure)

    /**
     * Applies a plugin or script, using the given options provided as a map. Does nothing if the plugin has already been applied.
     *
     * The given map is applied as a series of method calls to a newly created [ObjectConfigurationAction].
     * That is, each key in the map is expected to be the name of a method [ObjectConfigurationAction] and the value to be compatible arguments to that method.
     *
     * The following options are available:
     *
     * - `from`: A script to apply. Accepts any path supported by [org.gradle.api.Project.uri].
     * - `plugin`: The id or implementation class of the plugin to apply.
     * - `to`: The target delegate object or objects. The default is this plugin aware object. Use this to configure objects other than this object.
     *
     * @param options the options to use to configure and [ObjectConfigurationAction] before “executing” it
     */
    override fun apply(options: MutableMap<String, *>) = settings.apply(options)
}


private
fun scriptApiFor(settings: Settings): Script {
    val fileLookup = settings.serviceOf<FileLookup>()
    val fileOps = DefaultFileOperations(
        fileLookup.getFileResolver(settings.rootDir),
        null,
        null,
        settings.serviceOf<Instantiator>(),
        fileLookup,
        settings.serviceOf<DirectoryFileTreeFactory>(),
        settings.serviceOf<StreamHasher>(),
        settings.serviceOf<FileHasher>())
    return GradleSettingsScriptApi(settings as SettingsInternal, fileOps, fileOps)
}


/**
 * [Script] implementation for [Settings].
 */
private
class GradleSettingsScriptApi(
    private val settings: Settings,
    private val fileOperations: FileOperations,
    private val processOperations: ProcessOperations,
    private val logger: Logger = Logging.getLogger(Settings::class.java),
    private val logging: LoggingManager = settings.serviceOf<LoggingManager>(),
    private val objectFactory: ObjectFactory = settings.serviceOf<ObjectFactory>(),
    private val providerFactory: ProviderFactory = settings.serviceOf<ProviderFactory>()) : Script {

    override fun buildscript(configureClosure: Closure<*>) {
        configureClosure.call(settings.buildscript)
    }

    override fun getBuildscript(): ScriptHandler = settings.buildscript
    override fun apply(closure: Closure<*>) = settings.apply { closure.call(it) }
    override fun apply(options: MutableMap<String, *>) = settings.apply(options)

    override fun getLogger(): Logger = logger
    override fun getLogging(): LoggingManager = logging

    override fun <T : Any?> property(clazz: Class<T>): PropertyState<T> = objectFactory.property(clazz) as PropertyState
    override fun <T : Any?> provider(value: Callable<T>): Provider<T> = providerFactory.provider(value)

    override fun file(path: Any): File = fileOperations.file(path)
    override fun file(path: Any, validation: PathValidation): File = fileOperations.file(path, validation)
    override fun files(vararg paths: Any?): ConfigurableFileCollection = fileOperations.files(paths)
    override fun files(paths: Any, configureClosure: Closure<*>): ConfigurableFileCollection = fileOperations.files(paths, configureClosure)
    override fun relativePath(path: Any): String = fileOperations.relativePath(path)
    override fun uri(path: Any): URI = fileOperations.uri(path)
    override fun fileTree(baseDir: Any): ConfigurableFileTree = fileOperations.fileTree(baseDir)
    override fun fileTree(args: MutableMap<String, *>): ConfigurableFileTree = fileOperations.fileTree(args)
    override fun fileTree(baseDir: Any, configureClosure: Closure<*>): ConfigurableFileTree = fileOperations.fileTree(baseDir).also { configureClosure.call(it) }
    override fun zipTree(zipPath: Any): FileTree = fileOperations.zipTree(zipPath)
    override fun tarTree(tarPath: Any): FileTree = fileOperations.tarTree(tarPath)
    override fun mkdir(path: Any): File = fileOperations.mkdir(path)
    override fun delete(vararg paths: Any?): Boolean = fileOperations.delete(*paths)
    override fun copy(closure: Closure<*>): WorkResult = fileOperations.copy { closure.call(it) }
    override fun copySpec(closure: Closure<*>): CopySpec = fileOperations.copySpec().also { closure.call(it) }
    override fun getResources(): ResourceHandler = fileOperations.resources

    override fun exec(closure: Closure<*>): ExecResult = processOperations.exec { closure.call(it) }
    override fun exec(action: Action<in ExecSpec>): ExecResult = processOperations.exec(action)
    override fun javaexec(closure: Closure<*>): ExecResult = processOperations.javaexec { closure.call(it) }
    override fun javaexec(action: Action<in JavaExecSpec>): ExecResult = processOperations.javaexec(action)
}
