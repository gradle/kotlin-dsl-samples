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

package org.gradle.kotlin.dsl.provider

import org.gradle.groovy.scripts.internal.ScriptSourceHasher

import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.logging.progress.ProgressLoggerFactory

import org.gradle.kotlin.dsl.cache.ScriptCache
import org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider
import org.gradle.kotlin.dsl.support.ImplicitImports

import org.gradle.plugin.use.internal.PluginRequestApplicator


internal
object BuildServices {

    @Suppress("unused")
    fun createClassPathModeExceptionCollector() =
        ClassPathModeExceptionCollector()

    @Suppress("unused")
    fun createKotlinScriptEvaluator(
        classPathProvider: KotlinScriptClassPathProvider,
        classloadingCache: KotlinScriptClassloadingCache,
        pluginRequestsHandler: PluginRequestsHandler,
        pluginRequestApplicator: PluginRequestApplicator,
        embeddedKotlinProvider: EmbeddedKotlinProvider,
        classPathModeExceptionCollector: ClassPathModeExceptionCollector,
        kotlinScriptBasePluginsApplicator: KotlinScriptBasePluginsApplicator,
        scriptSourceHasher: ScriptSourceHasher,
        classPathHasher: ClasspathHasher,
        scriptCache: ScriptCache,
        implicitImports: ImplicitImports,
        progressLoggerFactory: ProgressLoggerFactory
    ): KotlinScriptEvaluator =

        StandardKotlinScriptEvaluator(
            classPathProvider,
            classloadingCache,
            pluginRequestApplicator,
            pluginRequestsHandler,
            embeddedKotlinProvider,
            classPathModeExceptionCollector,
            kotlinScriptBasePluginsApplicator,
            scriptSourceHasher,
            classPathHasher,
            scriptCache,
            implicitImports,
            progressLoggerFactory)
}
