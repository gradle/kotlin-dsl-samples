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
package org.gradle.script.lang.kotlin.provider

import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.api.Project

import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.internal.classpath.ClassPath
import org.gradle.script.lang.kotlin.support.serviceOf

import java.io.File
import java.io.Serializable


/**
 * Kotlin script template classpath model.
 */
interface KotlinScriptTemplateClassPathModel {

    /**
     * Classpath required to load Kotlin script template classes.
     */
    val classPath: List<File>
}


internal
data class StandardKotlinScriptTemplateClassPathModel(
    override val classPath: List<File>) : KotlinScriptTemplateClassPathModel, Serializable


internal
object KotlinScriptTemplateClassPathModelBuilder : ToolingModelBuilder {

    private
    val gradleModules = listOf("gradle-core", "gradle-tooling-api")

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.script.lang.kotlin.provider.KotlinScriptTemplateClassPathModel"

    override fun buildAll(modelName: String, project: Project): KotlinScriptTemplateClassPathModel =
        project.serviceOf<ModuleRegistry>().run {
            StandardKotlinScriptTemplateClassPathModel(
                gradleModules.map { getModule(it) }
                    .flatMap { it.allRequiredModules }
                    .fold(ClassPath.EMPTY) { classPath, module -> classPath + module.classpath }
                    .asFiles)
        }
}
