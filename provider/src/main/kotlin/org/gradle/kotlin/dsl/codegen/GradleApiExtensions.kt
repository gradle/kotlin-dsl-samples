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


/**
 * Generate and write Gradle API extensions to the given file.
 *
 * Limitations:
 * - supports only what is needed by current generators
 * - does not support generating extensions for functions with type parameters having multiple bounds (Kotlin where clause)
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

    val signatureKey: Int = Objects.hash(targetType.sourceName, targetType.typeParameters, name, parameters.map { it.type })

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
        append(name)
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
object SourceNames {
    const val javaClass = "java.lang.Class"
    const val groovyClosure = "groovy.lang.Closure"
    const val gradleAction = "org.gradle.api.Action"
    const val gradleTypeOf = "org.gradle.api.reflect.TypeOf"
    const val kotlinClass = "kotlin.reflect.KClass"
    const val kotlinArray = "kotlin.Array"
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
val ApiTypeUsage.isKotlinArray
    get() = sourceName == SourceNames.kotlinArray


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
                "${f.name}(${f.parameters.toFunctionParametersInvocationString(override = kClassParameterInvocationOverride)})")
        }
}


private
val reifiedTypeParametersExtensionsGenerator: ExtensionsForType = { type: ApiType ->

    type.gradleApiFunctions.asSequence()
        .withSingleTypeParameterAndTypeOfOrClassParameter()
        .sortedWithTypeOfTakingFunctionsFirst()
        .map { f ->
            val reifiedTypeParameter = f.typeParameters.first()
            val reifiedParameter =
                Pair(
                    isParameterWithTypeArgument(SourceNames.gradleTypeOf, reifiedTypeParameter),
                    isParameterWithTypeArgument(SourceNames.javaClass, reifiedTypeParameter)
                ).let { (isTypeOfT, isJavaClassOfT) ->
                    f.parameters.first { p -> isTypeOfT(p) || isJavaClassOfT(p) }
                }
            val isReifiedTypeOf = reifiedParameter.type.sourceName == SourceNames.gradleTypeOf

            val reifiedTypeParameterInvocationOverride = { index: Int, p: ApiFunctionParameter ->
                if (isReifiedTypeOf && p.index.toParameterName() == reifiedParameter.index.toParameterName()) "typeOf<${reifiedTypeParameter.sourceName}>()"
                else if (p.index.toParameterName() == reifiedParameter.index.toParameterName()) "${reifiedTypeParameter.sourceName}::class.java"
                else kClassParameterInvocationOverride(index, p)
            }

            KotlinExtensionFunction(
                "Kotlin extension function with reified type parameter for [${type.sourceName}.${f.name}]",
                f.isIncubating, f.isDeprecated, true,
                listOf(Pair(reifiedTypeParameter, true)) + type.typeParameters.map { Pair(it, false) },
                type, f.name, f.parameters.minus(reifiedParameter).map(kClassParameterDeclarationOverride), f.returnType,
                "${f.name}(${f.parameters.toFunctionParametersInvocationString(listOf(reifiedParameter.index), reifiedTypeParameterInvocationOverride)})")
        }
}


private
val kClassParameterDeclarationOverride = { p: ApiFunctionParameter ->
    if (p.type.isJavaClass) p.toKotlinClass()
    else if (p.type.isKotlinArray && p.type.typeArguments.single().isJavaClass)
        ApiFunctionParameter(p.index, ApiTypeUsage(SourceNames.kotlinArray, p.type.isNullable, null, listOf(ApiTypeUsage(SourceNames.kotlinClass, false, null, p.type.typeArguments.single().typeArguments))))
    else p
}


private
val kClassParameterInvocationOverride = { index: Int, p: ApiFunctionParameter ->
    if (p.type.isJavaClass) "${index.toParameterName()}.java"
    else if (p.type.isKotlinArray && p.type.typeArguments.single().isJavaClass) "*${index.toParameterName()}.map { it.java }.toTypedArray()"
    else null
}


private
fun Sequence<ApiFunction>.withClassOrClassArrayParameter() =
    filter { f -> f.parameters.any { it.type.isJavaClass || it.type.isKotlinArray && it.type.typeArguments.single().isJavaClass } }


private
fun isParameterWithTypeArgument(typeSourceName: String, typeArgument: ApiTypeUsage) = { p: ApiFunctionParameter ->
    p.type.sourceName == typeSourceName && !p.type.isRaw && p.type.typeArguments.singleOrNull()?.sourceName == typeArgument.sourceName
}


private
fun ApiFunction.hasParameterWithTypeArgument(typeSourceName: String, typeArgument: ApiTypeUsage) =
    isParameterWithTypeArgument(typeSourceName, typeArgument).let { predicate ->
        parameters.any(predicate)
    }


private
fun Sequence<ApiFunction>.withSingleTypeParameterAndTypeOfOrClassParameter() =
    filter { f ->
        f.typeParameters.size == 1
            && (f.hasParameterWithTypeArgument(SourceNames.gradleTypeOf, f.typeParameters.first())
            || f.hasParameterWithTypeArgument(SourceNames.javaClass, f.typeParameters.first()))
    }


private
fun Sequence<ApiFunction>.sortedWithTypeOfTakingFunctionsFirst() =
    sortedBy { f ->
        if (f.hasParameterWithTypeArgument(SourceNames.gradleTypeOf, f.typeParameters.first())) 0
        else 1
    }


private
fun ApiFunctionParameter.toKotlinClass() =
    ApiFunctionParameter(index, type.toKotlinClass())


private
fun ApiTypeUsage.toKotlinClass() =
    ApiTypeUsage(SourceNames.kotlinClass, isNullable, null, typeArguments)


private
fun List<ApiFunctionParameter>.toFunctionParametersString(inlineFunction: Boolean = false): String =
    takeIf { it.isNotEmpty() }
        ?.mapIndexed { idx, p ->
            if (idx == size - 1 && p.type.isKotlinArray) "vararg ${idx.toParameterName()}: ${p.type.typeArguments.single().toTypeArgumentString()}"
            else if (p.type.isGradleAction) "${if (inlineFunction) "noinline " else ""}${idx.toParameterName()}: ${p.type.typeArguments.single().toTypeArgumentString()}.() -> Unit"
            else "${idx.toParameterName()}: ${p.type.toTypeArgumentString()}"
        }
        ?.joinToString(separator = ", ")
        ?: ""


private
fun List<ApiFunctionParameter>.toFunctionParametersInvocationString(skippedIndices: List<Int> = emptyList(), override: (Int, ApiFunctionParameter) -> String? = { _, _ -> null }): String =
    takeIf { it.isNotEmpty() }
        ?.mapIndexed { idx, p ->
            (p.index - skippedIndices.count { it <= idx }).let { index ->
                override(index, p) ?: index.toParameterName()
            }
        }
        ?.joinToString(separator = ", ")
        ?: ""


private
fun Int.toParameterName() =
    "p$this"


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
