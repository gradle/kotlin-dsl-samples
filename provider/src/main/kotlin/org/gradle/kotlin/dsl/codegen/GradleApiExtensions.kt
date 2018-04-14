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
 * - when applicable, creates extensions that:
 *   - replace `Class<T>` with `KClass<T>`
 *   - replace `Class<T>[]` with `Array<KClass<T>>`
 *   - replace `Collection<Class<T>>` with `Collection<KClass<T>
 *   - replace Groovy named arguments `Map<String, ?>` first parameter pattern with last vararg parameter of `Pair<String, *>`
 * - creates supplementary extensions with reified parameter type replacing first function parameter of type `TypeOf<T>` or `Class<T>`
 *
 * Limitations:
 * - skips on cases not found in the Gradle API
 * - does not support generating extensions for functions with type parameters having multiple bounds (Kotlin where clause)
 */
internal
fun writeGradleApiExtensionsTo(file: File, jars: Iterable<File>) {
    jars.filter(gradleJarsFilter).toList().let { gradleJars ->
        file.bufferedWriter().use {
            it.apply {
                write(fileHeader)
                write("\n")
                apiTypeProviderFor(gradleJars, parameterNamesSupplierFor(gradleJars)).use { api ->
                    gradleApiExtensionDeclarationsFor(api).forEach {
                        write("\n$it")
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
    api.allTypes()
        .filter { type -> type.isGradleApi }
        .flatMap { type -> kotlinExtensionFunctionsFor(type) }
        .distinctBy { extension -> extension.signatureKey }
        .map { it.toKotlinString() }


private
fun kotlinExtensionFunctionsFor(type: ApiType): Sequence<KotlinExtensionFunction> =
    type.gradleApiFunctions.asSequence()
        .sortedWithTypeOfTakingFunctionsFirst()
        .flatMap { function ->

            val candidateFor = object {
                val groovyNamedArgumentsToVarargs = function.parameters.firstOrNull()?.type?.isMapOfStringStar == true
                val javaClassToKotlinClass = function.parameters.any {
                    it.type.isJavaClass || (it.type.isKotlinArray && it.type.typeArguments.single().isJavaClass) || (it.type.isKotlinCollection && it.type.typeArguments.single().isJavaClass)
                }
                val reifiedParameter = function.parameters.any { p ->
                    (p.type.isGradleTypeOf || p.type.isJavaClass)
                        && !p.type.isRaw && !p.type.typeArguments.single().isStarProjectionTypeUsage
                }

                val extension
                    get() = groovyNamedArgumentsToVarargs || javaClassToKotlinClass || reifiedParameter
            }

            if (!candidateFor.extension) {
                return@flatMap emptySequence<KotlinExtensionFunction>()
            }

            val extensionTypeParameters = function.typeParameters + type.typeParameters

            val extensionFunction = KotlinExtensionFunction(
                description = "Kotlin extension function ${if (candidateFor.javaClassToKotlinClass) "taking [kotlin.reflect.KClass] " else ""}for [${type.sourceName}.${function.name}]",
                isIncubating = function.isIncubating,
                isDeprecated = function.isDeprecated,
                typeParameters = extensionTypeParameters,
                targetType = type,
                name = function.name,
                parameters = function.newMappedParameters().groovyNamedArgumentsToVarargs().javaClassToKotlinClass(),
                returnType = function.returnType)

            if (!candidateFor.reifiedParameter) sequenceOf(extensionFunction)
            else sequenceOf(
                extensionFunction,
                KotlinExtensionFunction(
                    description = "Kotlin extension function with reified type parameter for [${type.sourceName}.${function.name}]",
                    isIncubating = function.isIncubating,
                    isDeprecated = function.isDeprecated,
                    typeParameters = extensionTypeParameters,
                    targetType = type,
                    name = function.name,
                    parameters = function.newMappedParameters().groovyNamedArgumentsToVarargs().javaClassToKotlinClass().typeOfOrKClassToReifiedTypeParameter(extensionTypeParameters),
                    returnType = function.returnType))
        }


private
fun Sequence<ApiFunction>.sortedWithTypeOfTakingFunctionsFirst() =
    sortedBy { f ->
        if (f.parameters.any { it.type.isGradleTypeOf }) 0
        else 1
    }


private
fun ApiFunction.newMappedParameters() =
    parameters.map { MappedApiFunctionParameter(it) }


private
data class MappedApiFunctionParameter(
    val original: ApiFunctionParameter,
    val index: Int = original.index,
    val type: ApiTypeUsage = original.type,
    val invocation: String = "`${original.name ?: "p$index"}`",
    val reifiedAs: ApiTypeUsage? = null
) {
    val name: String
        get() = original.name ?: "p$index"
}


private
fun List<MappedApiFunctionParameter>.groovyNamedArgumentsToVarargs() =
    firstOrNull()?.takeIf { it.type.isMapOfStringStar }?.let { first ->
        drop(1) + first.copy(
            type = ApiTypeUsage(
                sourceName = SourceNames.kotlinArray,
                typeArguments = listOf(
                    ApiTypeUsage(
                        "Pair",
                        typeArguments = listOf(
                            ApiTypeUsage("String"), starProjectionTypeUsage)))),
            invocation = "mapOf(*${first.invocation})")
    } ?: this


private
fun List<MappedApiFunctionParameter>.javaClassToKotlinClass() =
    map { p ->
        if (p.type.isJavaClass) p.copy(type = p.type.toKotlinClass(), invocation = "${p.invocation}.java")
        else if (p.type.isKotlinArray && p.type.typeArguments.single().isJavaClass) p.copy(type = p.type.toArrayOfKotlinClasses(), invocation = "*${p.invocation}.map { it.java }.toTypedArray()")
        else if (p.type.isKotlinCollection && p.type.typeArguments.single().isJavaClass) p.copy(type = p.type.toCollectionOfKotlinClasses(), invocation = "${p.invocation}.map { it.java }")
        else p
    }


private
fun List<MappedApiFunctionParameter>.typeOfOrKClassToReifiedTypeParameter(typeParameters: List<ApiTypeUsage>): List<MappedApiFunctionParameter> {
    var foundTypeOf = false
    var foundKotlinClass = false
    return map { p ->
        if (foundTypeOf || foundKotlinClass || (!p.type.isGradleTypeOf && !p.type.isKotlinClass)) return@map p
        if (p.type.isGradleTypeOf) foundTypeOf = true
        if (p.type.isKotlinClass) foundKotlinClass = true
        val reified = p.type.typeArguments.single().let { typeArgument -> typeParameters.singleOrNull { it.sourceName == typeArgument.sourceName } }
            ?: ApiTypeUsage(
                p.name.capitalize(),
                bounds = listOfNotNull(p.type.typeArguments.single().takeIf { !it.isStarProjectionTypeUsage }))
        p.copy(
            index = -1,
            reifiedAs = reified,
            invocation = if (p.type.isGradleTypeOf) "typeOf<`${reified.sourceName}`>()" else "`${reified.sourceName}`::class.java")
    }
}


private
data class KotlinExtensionFunction(
    val description: String,
    val isIncubating: Boolean,
    val isDeprecated: Boolean,
    val typeParameters: List<ApiTypeUsage>,
    val targetType: ApiType,
    val name: String,
    val parameters: List<MappedApiFunctionParameter>,
    val returnType: ApiTypeUsage
) {

    val signatureKey: Int
        get() = Objects.hash(
            targetType.sourceName,
            name,
            parameters.filter { it.reifiedAs == null }.map { it.type.key })

    fun toKotlinString(): String = StringBuilder().apply {

        val reifiedParameter = parameters.singleOrNull { it.reifiedAs != null }
        val isInline = reifiedParameter != null

        val actualTypeParameters =
            if (reifiedParameter == null) typeParameters.map { it to false }
            else listOf(reifiedParameter.reifiedAs!! to true) + typeParameters.filterNot { it.sourceName == reifiedParameter.reifiedAs.sourceName }.map { it to false }

        appendln("""
            /**
             * $description.
             */
        """.trimIndent())
        if (isDeprecated) appendln("""@Deprecated("Deprecated Gradle API")""")
        if (isIncubating) appendln("@org.gradle.api.Incubating")
        if (isInline) append("inline ")
        append("fun ")
        if (actualTypeParameters.isNotEmpty()) append("${actualTypeParameters.joinInAngleBrackets { (if (it.second) "reified " else "") + it.first.toTypeParameterString() }} ")
        append(targetType.sourceName)
        if (targetType.typeParameters.isNotEmpty()) append(targetType.typeParameters.toTypeArgumentsString(targetType))
        append(".")
        append("`$name`")
        append("(")
        append(parameters.toDeclarationString(isInline))
        append("): ")
        append(returnType.toTypeArgumentString())
        appendln(" =")
        appendln("`$name`(${parameters.toInvocationString()})".prependIndent())
        appendln()
    }.toString()


    private
    fun List<MappedApiFunctionParameter>.toDeclarationString(inlineFunction: Boolean = false): String =
        filter { it.reifiedAs == null }.takeIf { it.isNotEmpty() }?.let { list ->
            list.mapIndexed { index, p ->
                if (index == list.size - 1 && p.type.isKotlinArray) "vararg ${p.name}: ${p.type.typeArguments.single().toTypeArgumentString()}"
                else if (p.type.isGradleAction) "${if (inlineFunction) "noinline " else ""}${p.name}: ${p.type.typeArguments.single().toTypeArgumentString()}.() -> Unit"
                else "${p.name}: ${p.type.toTypeArgumentString()}"
            }.joinToString(separator = ", ")
        } ?: ""


    private
    fun List<MappedApiFunctionParameter>.toInvocationString(): String =
        takeIf { it.isNotEmpty() }
            ?.sortedBy { it.original.index }
            ?.joinToString(separator = ", ") { it.invocation }
            ?: ""
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
        type?.typeParameters?.isNotEmpty() == true -> List(type.typeParameters.size) { starProjectionTypeUsage }
        else -> emptyList()
    }


private
fun <T> List<T>?.joinInAngleBrackets(transform: (T) -> CharSequence = { it.toString() }) =
    this?.takeIf { it.isNotEmpty() }
        ?.joinToString(separator = ", ", prefix = "<", postfix = ">", transform = transform)
        ?: ""


private
val ApiTypeUsage.isMapOfStringStar
    get() = sourceName == "kotlin.collections.Map"
        && typeArguments[0].sourceName == "String"
        && typeArguments[1].isStarProjectionTypeUsage


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
val ApiTypeUsage.isKotlinClass
    get() = sourceName == SourceNames.kotlinClass


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
