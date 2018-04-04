/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.kotlin.dsl.codegen

import org.gradle.kotlin.dsl.support.classPathBytecodeRepositoryFor

import java.io.File


internal
fun writeGradleApiExtensionsTo(file: File, gradleJars: Iterable<File>) {
    file.bufferedWriter().use {
        it.apply {
            write(fileHeader)
            ApiTypeProvider(classPathBytecodeRepositoryFor(gradleJars.toList())).use { api ->
                gradleApiExtensionDeclarationsFor(api).forEach {
                    write("\n")
                    write(it)
                    write("\n")
                }
            }
        }
    }
}


internal
fun gradleApiExtensionDeclarationsFor(api: ApiTypeProvider): Sequence<String> =
    api.allTypes().filter { it.isGradleApi }.flatMap { type ->
        sequenceOf(reifiedTypeParametersExtensionsGenerator)
            .flatMap { generator -> generator(type) }
    }


private
val reifiedTypeParametersExtensionsGenerator = { type: ApiType ->

    fun ApiTypeUsage.isClassParameterOf(formalTypeParameter: ApiTypeUsage) =
        sourceName == "java.lang.Class" && !isRaw && typeParameters[0].sourceName == formalTypeParameter.sourceName

    type.gradleApiFunctions.asSequence()
        .filter {
            if (it.formalTypeParameters.size != 1) false
            else {
                val formalTypeParameter = it.formalTypeParameters[0]
                it.parameters.asSequence().singleOrNull { (_, type) -> type.isClassParameterOf(formalTypeParameter) } != null
            }
        }
        .flatMap { f ->

            val reifiedFormalTypeParameter = f.formalTypeParameters[0]
            val extensionFormalTypeParameters = (listOf("reified $reifiedFormalTypeParameter") + type.formalTypeParameters.map { "$it" })
                .joinToString(separator = ", ", prefix = "<", postfix = ">")
            val extendedTypeTypeParameters = type.formalTypeParameters.takeIf { it.isNotEmpty() }
                ?.joinToString(separator = ", ", prefix = "<", postfix = ">") { it.sourceName }
                ?: ""
            val params = f.parameters.filterNot { it.value.isClassParameterOf(reifiedFormalTypeParameter) }
            val invocationParams = f.parameters.map {
                if (it.value.isClassParameterOf(reifiedFormalTypeParameter)) "${reifiedFormalTypeParameter.sourceName}::class.java"
                else it.key
            }.joinToString(", ")

            sequenceOf(
                """
                |inline fun ${extensionFormalTypeParameters.takeIf { it.isNotEmpty() }?.let { "$it " }
                    ?: ""}${type.sourceName}$extendedTypeTypeParameters.${f.name}(${params.toFunctionParametersString()})${f.returnType.let { ": $it" }} =
                |   ${f.name}($invocationParams)
                """.trimMargin())
        }
}


internal
val ApiType.isGradleApi: Boolean
    get() = sourceName !in typeSourceNameBlackList
        && PublicApi.excludes.none { it.matches(sourceName) }
        && PublicApi.includes.any { it.matches(sourceName) }
        && isPublic


private
val ApiType.gradleApiFunctions: List<ApiFunction>
    get() = functions.filter { it.isGradleApi && !it.isStatic }


private
val ApiFunction.isGradleApi: Boolean
    get() = name !in functionNameBlackList && isPublic


private
val typeSourceNameBlackList = emptyList<String>()


private
val functionNameBlackList = listOf("apply")


private
object PublicApi {
    val includes = listOf(
        "org/gradle/*",
        "org/gradle/api/**",
        "org/gradle/authentication/**",
        "org/gradle/buildinit/**",
        "org/gradle/caching/**",
        "org/gradle/concurrent/**",
        "org/gradle/deployment/**",
        "org/gradle/external/javadoc/**",
        "org/gradle/ide/**",
        "org/gradle/includedbuild/**",
        "org/gradle/ivy/**",
        "org/gradle/jvm/**",
        "org/gradle/language/**",
        "org/gradle/maven/**",
        "org/gradle/nativeplatform/**",
        "org/gradle/normalization/**",
        "org/gradle/platform/**",
        "org/gradle/play/**",
        "org/gradle/plugin/devel/**",
        "org/gradle/plugin/repository/*",
        "org/gradle/plugin/use/*",
        "org/gradle/plugin/management/*",
        "org/gradle/plugins/**",
        "org/gradle/process/**",
        "org/gradle/testfixtures/**",
        "org/gradle/testing/jacoco/**",
        "org/gradle/tooling/**",
        "org/gradle/swiftpm/**",
        "org/gradle/model/**",
        "org/gradle/testkit/**",
        "org/gradle/testing/**",
        "org/gradle/vcs/**",
        "org/gradle/workers/**")
        .map { it.replace("/", "\\.") }
        .map {
            when {
                it.endsWith("**") -> Regex("${it.dropLast(2)}.*")
                it.endsWith("*") -> Regex("${it.dropLast(1)}[A-Z].*")
                else -> throw InternalError("Should not happen")
            }
        }

    val excludes = listOf(
        Regex(".*\\.internal\\..*"),
        Regex("org\\.gradle\\.kotlin\\..*"))
}
