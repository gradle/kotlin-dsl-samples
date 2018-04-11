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

import java.io.File
import java.util.Objects
import java.util.Properties
import java.util.jar.JarFile


/**
 * Generate and write Gradle API extensions to the given file.
 *
 * ApiTypeProvider Limitations:
 * - supports Java byte code only, not Kotlin
 * - does not support nested Java arrays as method parameters
 * - does not distinguish co-variance and contra-variance
 *
 * GradleApiExtensions Limitations:
 * - does not support generating extensions for functions with type parameters having multiple bounds (Kotlin where clause)
 */
internal
fun writeGradleApiExtensionsTo(file: File, jars: Iterable<File>) {
    jars.filter(gradleJarsFilter).toList().let { gradleJars ->
        file.bufferedWriter().use {
            it.apply {
                write(fileHeader)
                apiTypeProviderFor(gradleJars, parameterNamesSupplierFor(gradleJars)).use { api ->
                    gradleApiExtensionDeclarationsFor(api).forEach {
                        write("\n$it\n")
                    }
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
fun parameterNamesSupplierFor(gradleJars: List<File>) =
    JarFile(gradleJars.single { it.name.startsWith("gradle-api-parameter-names-") }).use { jar ->
        jar.getInputStream(jar.getJarEntry("gradle-api-parameter-names.properties")).use { input ->
            Properties().also { it.load(input) }.let { index ->
                { key: String ->
                    index.getProperty(key, null)?.split(",")
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
            .map { it.toKotlinString() }
    }


private
data class KotlinExtensionFunction(
    val description: String,
    val isIncubating: Boolean,
    val isDeprecated: Boolean,
    val isInline: Boolean,
    val typeParameters: List<Pair<ApiTypeUsage, Boolean>>,
    val targetType: ApiType,
    val name: String,
    val parameters: List<ApiFunctionParameter>,
    val returnType: ApiTypeUsage,
    val expressionBody: String
) {

    val signatureKey: Int = Objects.hash(targetType.sourceName, name, parameters.map { it.type })

    fun toKotlinString(): String = StringBuilder().apply {
        appendln("""
            /**
             * $description.
             */
        """.trimIndent())
        if (isDeprecated) appendln("""@Deprecated("Deprecated Gradle API")""")
        if (isIncubating) appendln("@org.gradle.api.Incubating")
        if (isInline) append("inline ")
        append("fun ")
        if (typeParameters.isNotEmpty()) append("${typeParameters.joinInAngleBrackets { (if (it.second) "reified " else "") + it.first.toTypeParameterString() }} ")
        append(targetType.sourceName)
        if (targetType.typeParameters.isNotEmpty()) append(targetType.typeParameters.toTypeArgumentsString(targetType))
        append(".")
        append("`$name`")
        append("(")
        append(parameters.toFunctionParametersString(isInline))
        append("): ")
        append(returnType.toTypeArgumentString())
        appendln(" =")
        appendln(expressionBody.prependIndent())
        appendln()
    }.toString()
}


private
typealias ExtensionsForType = (ApiType) -> Sequence<KotlinExtensionFunction>


private
val kClassExtensionsGenerator: ExtensionsForType = { type: ApiType ->
    type.gradleApiFunctions.asSequence()
        .withClassOrClassArrayParameter()
        .map { f ->
            KotlinExtensionFunction(
                "Kotlin extension function taking [kotlin.reflect.KClass] for [${type.sourceName}.${f.name}]",
                f.isIncubating, f.isDeprecated, false,
                (f.typeParameters + type.typeParameters).map { Pair(it, false) },
                type, f.name, f.parameters.map(kClassParameterDeclarationOverride), f.returnType,
                "`${f.name}`(${f.parameters.toFunctionParametersInvocationString(override = kClassParameterInvocationOverride)})")
        }
}


private
val reifiedTypeParametersExtensionsGenerator: ExtensionsForType = { type: ApiType ->

    type.gradleApiFunctions.asSequence()
        .withNonRawNorStarTypeOfOrClassParameter()
        .sortedWithTypeOfTakingFunctionsFirst()
        .map { f ->
            val reifiedParameter = f.parameters.first { it.type.isGradleTypeOf || it.type.isJavaClass }
            val reifiedParameterTypeArgument = reifiedParameter.type.typeArguments.single()

            val reifiedTypeParameter = f.typeParameters.singleOrNull { it.sourceName == reifiedParameterTypeArgument.sourceName }
                ?: ApiTypeUsage(nextAvailableTypeParameterName(f.typeParameters + type.typeParameters), bounds = listOf(reifiedParameterTypeArgument))


            val extensionTypeParameters = listOf(Pair(reifiedTypeParameter, true)) +
                f.typeParameters.minus(reifiedTypeParameter).map { Pair(it, false) } +
                type.typeParameters.map { Pair(it, false) }

            val isReifiedTypeOf = reifiedParameter.type.isGradleTypeOf
            val reifiedTypeParameterInvocationOverride = { index: Int, p: ApiFunctionParameter ->
                if (isReifiedTypeOf && p.index == reifiedParameter.index) "typeOf<${reifiedTypeParameter.sourceName}>()"
                else if (p.index == reifiedParameter.index) "${reifiedTypeParameter.sourceName}::class.java"
                else kClassParameterInvocationOverride(index, p)
            }

            KotlinExtensionFunction(
                "Kotlin extension function with reified type parameter for [${type.sourceName}.${f.name}]",
                f.isIncubating, f.isDeprecated, true,
                extensionTypeParameters,
                type, f.name, f.parameters.minus(reifiedParameter).map(kClassParameterDeclarationOverride), f.returnType,
                "`${f.name}`(${f.parameters.toFunctionParametersInvocationString(listOf(reifiedParameter.index), reifiedTypeParameterInvocationOverride)})")
        }
}


private
val kClassParameterDeclarationOverride = { p: ApiFunctionParameter ->
    if (p.type.isJavaClass) p.copy(type = p.type.toKotlinClass())
    else if (p.type.isKotlinArray && p.type.typeArguments.single().isJavaClass) p.copy(type = p.type.toArrayOfKotlinClasses())
    else if (p.type.isKotlinCollection && p.type.typeArguments.single().isJavaClass) p.copy(type = p.type.toCollectionOfKotlinClasses())
    else p
}


private
val kClassParameterInvocationOverride = { index: Int, p: ApiFunctionParameter ->
    if (p.type.isJavaClass) "${gradleApiParameterNameFor(p, index)}.java"
    else if (p.type.isKotlinArray && p.type.typeArguments.single().isJavaClass) "*${gradleApiParameterNameFor(p, index)}.map { it.java }.toTypedArray()"
    else if (p.type.isKotlinCollection && p.type.typeArguments.single().isJavaClass) "${gradleApiParameterNameFor(p, index)}.map { it.java }"
    else null
}


private
fun Sequence<ApiFunction>.withClassOrClassArrayParameter() =
    filter { f ->
        f.parameters.any {
            it.type.isJavaClass
                || (it.type.isKotlinArray && it.type.typeArguments.single().isJavaClass)
                || (it.type.isKotlinCollection && it.type.typeArguments.single().isJavaClass)
        }
    }


private
fun Sequence<ApiFunction>.withNonRawNorStarTypeOfOrClassParameter() =
    filter { f -> f.parameters.any { p -> (p.type.isGradleTypeOf || p.type.isJavaClass) && !p.type.isRaw && p.type.typeArguments.single() != starProjectionTypeUsage } }


private
fun Sequence<ApiFunction>.sortedWithTypeOfTakingFunctionsFirst() =
    sortedBy { f ->
        if (f.parameters.any { it.type.isGradleTypeOf }) 0
        else 1
    }


private
fun ApiTypeUsage.toKotlinClass() =
    ApiTypeUsage(SourceNames.kotlinClass, isNullable, typeArguments = typeArguments)


private
fun ApiTypeUsage.toArrayOfKotlinClasses() =
    ApiTypeUsage(SourceNames.kotlinArray, isNullable, typeArguments = listOf(ApiTypeUsage(SourceNames.kotlinClass, typeArguments = typeArguments.single().typeArguments)))


private
fun ApiTypeUsage.toCollectionOfKotlinClasses() =
    ApiTypeUsage(SourceNames.kotlinCollection, isNullable, typeArguments = listOf(ApiTypeUsage(SourceNames.kotlinClass, typeArguments = typeArguments.single().typeArguments)))


private
val typeParameterPossibleNames: List<String> = "TUVWXYZABCDEFGHIJKLMNOPQRS".map { it.toString() }


private
fun nextAvailableTypeParameterName(existing: List<ApiTypeUsage>) =
    existing.map { it.sourceName }.let { existingNames ->
        typeParameterPossibleNames.first { it !in existingNames }
    }


private
fun List<ApiFunctionParameter>.toFunctionParametersString(inlineFunction: Boolean = false): String =
    takeIf { it.isNotEmpty() }
        ?.mapIndexed { index, p ->
            if (index == size - 1 && p.type.isKotlinArray) "vararg ${gradleApiParameterNameFor(p, index)}: ${p.type.typeArguments.single().toTypeArgumentString()}"
            else if (p.type.isGradleAction) "${if (inlineFunction) "noinline " else ""}${gradleApiParameterNameFor(p, index)}: ${p.type.typeArguments.single().toTypeArgumentString()}.() -> Unit"
            else "${gradleApiParameterNameFor(p, index)}: ${p.type.toTypeArgumentString()}"
        }
        ?.joinToString(separator = ", ")
        ?: ""


private
fun List<ApiFunctionParameter>.toFunctionParametersInvocationString(skippedIndices: List<Int> = emptyList(), override: (Int, ApiFunctionParameter) -> String? = { _, _ -> null }): String =
    takeIf { it.isNotEmpty() }
        ?.mapIndexed { idx, p ->
            (p.index - skippedIndices.count { it <= idx }).let { index ->
                override(index, p) ?: gradleApiParameterNameFor(p, index)
            }
        }
        ?.joinToString(separator = ", ")
        ?: ""


private
fun gradleApiParameterNameFor(parameter: ApiFunctionParameter, index: Int): String =
    parameter.name ?: "p$index"


private
fun Boolean.toKotlinNullabilityString(): String =
    if (this) "?" else ""


private
fun ApiTypeUsage.toTypeParameterString(): String =
    "$sourceName${
    bounds.takeIf { it.isNotEmpty() }?.let { " : ${it.single().toTypeParameterString()}" } ?: ""
    }${typeArguments.toTypeParametersString(type)}${isNullable.toKotlinNullabilityString()}"


private
fun List<ApiTypeUsage>.toTypeParametersString(type: ApiType? = null): String =
    rawTypesToStarProjections(type).joinInAngleBrackets { it.toTypeParameterString() }


private
fun ApiTypeUsage.toTypeArgumentString(): String =
    "$sourceName${typeArguments.toTypeArgumentsString(type)}${isNullable.toKotlinNullabilityString()}"


private
fun List<ApiTypeUsage>.toTypeArgumentsString(type: ApiType? = null): String =
    rawTypesToStarProjections(type).joinInAngleBrackets { it.toTypeArgumentString() }


private
fun List<ApiTypeUsage>.rawTypesToStarProjections(type: ApiType? = null): List<ApiTypeUsage> =
    when {
        isNotEmpty() -> this
        type?.typeParameters?.isNotEmpty() == true -> Array(type.typeParameters.size) { starProjectionTypeUsage }.toList()
        else -> emptyList()
    }


private
fun <T> List<T>?.joinInAngleBrackets(transform: (T) -> CharSequence = { it.toString() }) =
    this?.takeIf { it.isNotEmpty() }
        ?.joinToString(separator = ", ", prefix = "<", postfix = ">", transform = transform)
        ?: ""


private
val starProjectionTypeUsage = ApiTypeUsage("*")


private
object SourceNames {
    const val javaClass = "java.lang.Class"
    const val groovyClosure = "groovy.lang.Closure"
    const val gradleAction = "org.gradle.api.Action"
    const val gradleTypeOf = "org.gradle.api.reflect.TypeOf"
    const val kotlinClass = "kotlin.reflect.KClass"
    const val kotlinArray = "kotlin.Array"
    const val kotlinCollection = "kotlin.collections.Collection"
}


private
val ApiTypeUsage.isJavaClass
    get() = sourceName == SourceNames.javaClass


private
val ApiTypeUsage.isGroovyClosure
    get() = sourceName == SourceNames.groovyClosure


private
val ApiTypeUsage.isGradleAction
    get() = sourceName == SourceNames.gradleAction


private
val ApiTypeUsage.isGradleTypeOf
    get() = sourceName == SourceNames.gradleTypeOf


private
val ApiTypeUsage.isKotlinArray
    get() = sourceName == SourceNames.kotlinArray


private
val ApiTypeUsage.isKotlinCollection
    get() = sourceName == SourceNames.kotlinCollection


internal
val ApiType.isGradleApi: Boolean
    get() = sourceName !in typeSourceNameBlackList
        && PublicApi.excludes.none { it.matches(sourceName) }
        && PublicApi.includes.any { it.matches(sourceName) }
        && isPublic


private
val ApiType.gradleApiFunctions: List<ApiFunction>
    get() = functions.filter { it.isGradleApi && !it.isStatic && it.parameters.none { it.type.isGroovyClosure } }


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
