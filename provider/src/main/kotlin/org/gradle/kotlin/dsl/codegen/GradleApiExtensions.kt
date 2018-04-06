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

import org.gradle.api.reflect.TypeOf

import java.io.File


internal
fun writeGradleApiExtensionsTo(file: File, gradleJars: Iterable<File>) {
    file.bufferedWriter().use {
        it.apply {
            write(fileHeader)
            apiTypeProviderFor(gradleJars.filter { it.name.startsWith("gradle-") }.toList()).use { api ->
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
        sequenceOf(reifiedTypeParametersExtensionsGenerator, kClassExtensionsGenerator)
            .flatMap { generator -> generator(type) }
            .distinctBy { it.signatureKey }
            .map {
                """
                |${it.annotations}
                |${it.signature}
                |    ${it.implementation}
                """.trimMargin().trim()
            }
    }


private
typealias ExtensionsForType = (ApiType) -> Sequence<GeneratedGradleApiExtensionFunction>


private
val kClassExtensionsGenerator: ExtensionsForType = { type: ApiType ->
    type.gradleApiFunctions.asSequence()
        .filter { f -> f.parameters.none { it.type.sourceName == "groovy.lang.Closure" } && f.parameters.any { it.type.sourceName == "java.lang.Class" } }
        .map { f ->
            val annotations = f.extensionAnnotations

            val extendedTypeTypeParameters = type.formalTypeParameters.takeIf { it.isNotEmpty() }
                ?.joinToString(separator = ", ", prefix = "<", postfix = ">") { it.sourceName }
                ?: ""
            val params = f.parameters.map {
                if (it.type.sourceName == "java.lang.Class")
                    ApiFunctionParameter(it.name, ApiTypeUsage("kotlin.reflect.KClass", it.type.isNullable, null, it.type.typeParameters))
                else if (it.type.sourceName == "Array" && it.type.typeParameters.single().sourceName == "java.lang.Class")
                    ApiFunctionParameter(it.name, ApiTypeUsage("Array", it.type.isNullable, null, listOf(ApiTypeUsage("kotlin.reflect.KClass", false, null, it.type.typeParameters.single().typeParameters))))
                else it
            }
            val signatureLeft = "fun${(f.formalTypeParameters + type.formalTypeParameters).toFormalTypeParametersString().takeIf { it.isNotEmpty() }?.let { " $it" } ?: ""}"
            val signatureRight = "${type.sourceName}$extendedTypeTypeParameters.${f.name}(${params.toFunctionParametersString()}): ${f.returnType.toTypeParameterString()} ="

            val invocationParams = f.parameters.joinToString(", ") {
                if (it.type.sourceName == "java.lang.Class") "${it.name}.java"
                else if (it.type.sourceName == "Array" && it.type.typeParameters.single().sourceName == "java.lang.Class")
                    "*${it.name}.map { it.java }.toTypedArray()"
                else it.name
            }
            val implementation = "${f.name}($invocationParams)"

            GeneratedGradleApiExtensionFunction(annotations, signatureLeft, signatureRight, implementation)
        }
}


private
val reifiedTypeParametersExtensionsGenerator: ExtensionsForType = { type: ApiType ->

    fun ApiTypeUsage.isParameterMatching(sourceName: String, typeParameter: ApiTypeUsage) =
        this.sourceName == sourceName && !isRaw && typeParameters[0].sourceName == typeParameter.sourceName

    fun ApiTypeUsage.isTypeOfParameterOf(typeParameter: ApiTypeUsage) =
        isParameterMatching(TypeOf::class.java.canonicalName, typeParameter)

    fun ApiTypeUsage.isClassParameterOf(typeParameter: ApiTypeUsage) =
        isParameterMatching("java.lang.Class", typeParameter)

    type.gradleApiFunctions.asSequence()
        .filterNot { it.parameters.any { it.type.sourceName == "groovy.lang.Closure" } }
        .filter { f ->
            if (f.formalTypeParameters.size != 1) false
            else f.parameters.singleOrNull { it.type.isTypeOfParameterOf(f.formalTypeParameters[0]) } != null
                || f.parameters.singleOrNull { it.type.isClassParameterOf(f.formalTypeParameters[0]) } != null
        }
        .sortedBy { f ->
            if (f.parameters.any { it.type.isTypeOfParameterOf(f.formalTypeParameters[0]) }) 0
            else 10
        }
        .map { f ->

            val isTypeOf = f.parameters.any { it.type.isTypeOfParameterOf(f.formalTypeParameters[0]) }

            val reifiedFormalTypeParameter = f.formalTypeParameters[0]
            val extensionFormalTypeParameters = (listOf("reified ${reifiedFormalTypeParameter.toFormalTypeParameterString()}") + type.formalTypeParameters.map { it.toFormalTypeParameterString() })
                .joinToString(separator = ", ", prefix = "<", postfix = ">")
            val extendedTypeTypeParameters = type.formalTypeParameters.takeIf { it.isNotEmpty() }
                ?.joinToString(separator = ", ", prefix = "<", postfix = ">") { it.toTypeParameterString() }
                ?: ""
            lateinit var reifiedParamName: String
            val params = f.parameters.filterNot { entry ->
                if (isTypeOf) entry.type.isTypeOfParameterOf(reifiedFormalTypeParameter).also { if (it) reifiedParamName = entry.name }
                else entry.type.isClassParameterOf(reifiedFormalTypeParameter).also { if (it) reifiedParamName = entry.name }
            }.map {
                if (it.type.sourceName == "java.lang.Class")
                    ApiFunctionParameter(it.name, ApiTypeUsage("kotlin.reflect.KClass", it.type.isNullable, null, it.type.typeParameters))
                else it
            }
            val invocationParams = f.parameters.map {
                if (isTypeOf && it.name == reifiedParamName) "typeOf<${reifiedFormalTypeParameter.sourceName}>()"
                else if (it.name == reifiedParamName) "${reifiedFormalTypeParameter.sourceName}::class.java"
                else if (it.type.sourceName == "java.lang.Class") "${it.name}.java"
                else it.name
            }.joinToString(", ")

            val signatureLeft = "inline fun${extensionFormalTypeParameters.takeIf { it.isNotEmpty() }?.let { " $it" } ?: ""}"
            val signatureRight = "${type.sourceName}$extendedTypeTypeParameters.${f.name}(${params.toFunctionParametersString(true)})${f.returnType.let { ": ${it.toTypeParameterString()}" }} ="
            val implementation = "${f.name}($invocationParams)"

            GeneratedGradleApiExtensionFunction(f.extensionAnnotations, signatureLeft, signatureRight, implementation)
        }
}


private
class GeneratedGradleApiExtensionFunction(
    val annotations: String,
    signatureLeft: String,
    signatureRight: String,
    val implementation: String
) {
    val signature = "$signatureLeft $signatureRight"
    val signatureKey = signatureRight.replace(Regex("p[0-9]: "), "pX: ")
}


private
val ApiFunction.extensionAnnotations: String
    get() = if (isDeprecated && isIncubating) "@Deprecated(\"Deprecated Gradle API\")\n@org.gradle.api.Incubating"
    else if (isDeprecated) "@Deprecated(\"Deprecated Gradle API\")"
    else if (isIncubating) "@org.gradle.api.Incubating"
    else ""


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
val functionNameBlackList = listOf("<init>", "apply")


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
