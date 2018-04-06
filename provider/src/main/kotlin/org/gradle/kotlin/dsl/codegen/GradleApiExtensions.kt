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

import org.gradle.api.Action
import org.gradle.api.reflect.TypeOf

import java.io.File

/**
 * Generate and write Gradle API extensions to the given file.
 *
 * Limitations:
 * - supports only what is needed by current generators
 * - does not support generating extensions for functions with formal type parameters having multiple bounds (Kotlin where clause)
 * - does not distinguish co-variance and contra-variance
 */
internal
fun writeGradleApiExtensionsTo(file: File, gradleJars: Iterable<File>) {
    file.bufferedWriter().use {
        it.apply {
            write(fileHeader)
            apiTypeProviderFor(gradleJars.filter(gradleJarsFilter).toList()).use { api ->
                gradleApiExtensionDeclarationsFor(api).forEach {
                    write("\n$it\n")
                }
            }
        }
    }
}


private
val gradleJarsFilter = { jar: File ->
    jar.name.startsWith("gradle-") && !jar.name.startsWith("gradle-kotlin-")
}


internal
fun gradleApiExtensionDeclarationsFor(api: ApiTypeProvider): Sequence<String> =
    api.allTypes().filter { it.isGradleApi }.flatMap { type ->
        sequenceOf(reifiedTypeParametersExtensionsGenerator, kClassExtensionsGenerator)
            .flatMap { generator -> generator(type) }
            .distinctBy { it.signatureKey }
            .map {
                "${it.annotations}\n${it.signature}\n    ${it.implementation}".trim()
            }
    }


private
class GeneratedGradleApiExtensionFunction(
    val annotations: String,
    signatureLeft: String,
    signatureRight: String,
    val implementation: String
) {
    companion object {
        private
        val signatureKeyRegex = Regex("p[0-9]: ")
    }

    val signature = "$signatureLeft $signatureRight"

    val signatureKey = signatureRight.replace(signatureKeyRegex, "pX: ").hashCode()
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
        .filter { f ->
            f.parameters.none { it.type.sourceName == "groovy.lang.Closure" }
                && f.formalTypeParameters.size == 1
                && (f.parameters.singleOrNull { it.type.isTypeOfParameterOf(f.formalTypeParameters[0]) } != null
                || f.parameters.singleOrNull { it.type.isClassParameterOf(f.formalTypeParameters[0]) } != null)
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
            val params = f.parameters.filterNot { p ->
                if (isTypeOf) p.type.isTypeOfParameterOf(reifiedFormalTypeParameter).also { if (it) reifiedParamName = p.name }
                else p.type.isClassParameterOf(reifiedFormalTypeParameter).also { if (it) reifiedParamName = p.name }
            }.map { p ->
                if (p.type.sourceName == "java.lang.Class")
                    ApiFunctionParameter(p.name, ApiTypeUsage("kotlin.reflect.KClass", p.type.isNullable, null, p.type.typeParameters))
                else p
            }
            val invocationParams = f.parameters.joinToString(", ") { p ->
                if (isTypeOf && p.name == reifiedParamName) "typeOf<${reifiedFormalTypeParameter.sourceName}>()"
                else if (p.name == reifiedParamName) "${reifiedFormalTypeParameter.sourceName}::class.java"
                else if (p.type.sourceName == "java.lang.Class") "${p.name}.java"
                else p.name
            }

            val signatureLeft = "inline fun${extensionFormalTypeParameters.takeIf { it.isNotEmpty() }?.let { " $it" } ?: ""}"
            val signatureRight = "${type.sourceName}$extendedTypeTypeParameters.${f.name}(${params.toFunctionParametersString(true)})${f.returnType.let { ": ${it.toTypeParameterString()}" }} ="
            val implementation = "${f.name}($invocationParams)"

            GeneratedGradleApiExtensionFunction(f.extensionAnnotations, signatureLeft, signatureRight, implementation)
        }
}


private
val ApiFunction.extensionAnnotations: String
    get() = if (isDeprecated && isIncubating) "@Deprecated(\"Deprecated Gradle API\")\n@org.gradle.api.Incubating"
    else if (isDeprecated) "@Deprecated(\"Deprecated Gradle API\")"
    else if (isIncubating) "@org.gradle.api.Incubating"
    else ""


internal
fun List<ApiFunctionParameter>.toFunctionParametersString(inlineFunction: Boolean = false): String =
    takeIf { it.isNotEmpty() }
        ?.mapIndexed { idx, p ->
            if (idx == size - 1 && p.type.sourceName == "Array") "vararg ${p.name}: ${p.type.typeParameters.single().toTypeParameterString()}"
            else if (p.type.sourceName == Action::class.java.canonicalName) "${if (inlineFunction) "noinline " else ""}${p.name}: ${p.type.typeParameters.single().toTypeParameterString()}.() -> Unit"
            else "${p.name}: ${p.type.toTypeParameterString()}"
        }
        ?.joinToString(separator = ", ")
        ?: ""


private
fun Boolean.toKotlinNullabilityString(): String =
    if (this) "?" else ""


internal
fun ApiTypeUsage.toFormalTypeParameterString(): String =
    "$sourceName${
    bounds.takeIf { it.isNotEmpty() }
        ?.joinToString(separator = ", ", prefix = " : ") { it.toFormalTypeParameterString() }
        ?: ""
    }${typeParameters.toFormalTypeParametersString(type)}${isNullable.toKotlinNullabilityString()}"


internal
fun List<ApiTypeUsage>.toFormalTypeParametersString(type: ApiType? = null, reified: Boolean = false): String =
    rawTypesToStarProjections(type).takeIf { it.isNotEmpty() }
        ?.joinToString(separator = ", ${if (reified) "reified " else ""}", prefix = "<${if (reified) "reified " else ""}", postfix = ">") {
            it.toFormalTypeParameterString()
        }
        ?: ""


internal
fun ApiTypeUsage.toTypeParameterString(): String =
    "$sourceName${typeParameters.toTypeParametersString(type)}${isNullable.toKotlinNullabilityString()}"


internal
fun List<ApiTypeUsage>.toTypeParametersString(type: ApiType? = null): String =
    rawTypesToStarProjections(type).takeIf { it.isNotEmpty() }
        ?.joinToString(separator = ", ", prefix = "<", postfix = ">") { it.toTypeParameterString() }
        ?: ""


private
fun List<ApiTypeUsage>.rawTypesToStarProjections(type: ApiType? = null): List<ApiTypeUsage> =
    when {
        isNotEmpty() -> this
        type?.formalTypeParameters?.isNotEmpty() == true -> Array(type.formalTypeParameters.size) { starProjectionTypeUsage }.toList()
        else -> emptyList()
    }


private
val starProjectionTypeUsage = ApiTypeUsage("*")


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


// TODO copy pasted from gradle/gradle build logic, should be provided by Gradle instead
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
