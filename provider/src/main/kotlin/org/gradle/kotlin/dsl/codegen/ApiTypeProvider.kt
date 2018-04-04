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

import org.gradle.kotlin.dsl.accessors.primitiveTypeStrings
import org.gradle.kotlin.dsl.support.ClassBytesRepository

import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassReader.SKIP_DEBUG
import org.jetbrains.org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_STATIC
import org.jetbrains.org.objectweb.asm.Opcodes.ASM6
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.signature.SignatureReader
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor
import org.jetbrains.org.objectweb.asm.tree.AnnotationNode
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode

import java.io.Closeable

import kotlin.LazyThreadSafetyMode.NONE


private
typealias ApiTypeIndex = (String) -> ApiType?


private
typealias ApiTypeSupplier = () -> ApiType


/**
 * Provides [ApiType] instances by Kotlin source name from a class path.
 *
 * Keeps JAR files open for fast lookup, must be closed.
 * Once closed, type graph navigation from [ApiType] and [ApiFunction] instances will throw.
 */
internal
class ApiTypeProvider(private val repository: ClassBytesRepository) : Closeable {

    private
    var closed = false

    private
    val apiTypesBySourceName = mutableMapOf<String, ApiTypeSupplier?>()

    override fun close() =
        try {
            repository.close()
        } finally {
            closed = true
        }

    fun type(sourceName: String): ApiType? =
        if (closed) throw IllegalStateException("ApiTypeProvider closed!")
        else apiTypesBySourceName.computeIfAbsent(sourceName) {
            repository.classBytesFor(sourceName)?.let { { apiTypeFor(sourceName, it) } }
        }?.invoke()

    fun allTypes(): Sequence<ApiType> =
        if (closed) throw IllegalStateException("ApiTypeProvider closed!")
        else repository.allClassesBytesBySourceName().mapNotNull { (sourceName, classBytes) ->
            apiTypesBySourceName.computeIfAbsent(sourceName) {
                { apiTypeFor(sourceName, classBytes()) }
            }
        }.map { it() }

    private
    fun apiTypeFor(sourceName: String, classBytes: ByteArray) =
        ApiType(sourceName, classNodeFor(classBytes), { type(it) })

    private
    fun classNodeFor(classBytes: ByteArray) =
        ClassNode().also {
            ClassReader(classBytes).accept(it, SKIP_DEBUG and SKIP_FRAMES)
        }
}


internal
class ApiType(
    val sourceName: String,
    private val delegate: ClassNode,
    private val typeIndex: ApiTypeIndex
) {

    val isPublic: Boolean =
        (ACC_PUBLIC and delegate.access) > 0

    val isDeprecated: Boolean
        get() = delegate.visibleAnnotations.hasDeprecated

    val isIncubating: Boolean
        get() = delegate.visibleAnnotations.hasIncubating

    val formalTypeParameters: List<ApiTypeUsage> by lazy(NONE) {
        visitedSignature?.formalTypeParameterDeclarations(typeIndex) ?: emptyList()
    }

    val functions: List<ApiFunction> by lazy(NONE) {
        delegate.methods.map { ApiFunction(this, it, typeIndex) }
    }

    override fun toString(): String {
        return "$sourceName${formalTypeParameters.toTypeParametersString(this)}"
    }

    private
    val visitedSignature: ClassSignatureVisitor? by lazy(NONE) {
        delegate.signature?.let { signature ->
            ClassSignatureVisitor().also { SignatureReader(signature).accept(it) }
        }
    }
}


internal
class ApiFunction(
    private val owner: ApiType,
    private val delegate: MethodNode,
    private val typeIndex: ApiTypeIndex
) {

    val name: String =
        delegate.name

    val isPublic: Boolean =
        (ACC_PUBLIC and delegate.access) > 0

    val isDeprecated: Boolean
        get() = owner.isDeprecated || delegate.visibleAnnotations.hasDeprecated

    val isIncubating: Boolean
        get() = owner.isIncubating || delegate.visibleAnnotations.hasIncubating

    val isStatic: Boolean =
        (ACC_STATIC and delegate.access) > 0

    val formalTypeParameters: List<ApiTypeUsage> by lazy(NONE) {
        visitedSignature?.formalTypeParameterDeclarations(typeIndex)
            ?: emptyList()
    }

    val parameters: Map<String, ApiTypeUsage> by lazy(NONE) {
        delegate.visibleParameterAnnotations?.map { it.hasNullable }.let { nullability ->
            visitedSignature?.parameters(typeIndex, nullability)
                ?: Type.getArgumentTypes(delegate.desc).mapIndexed { idx, p ->
                    val isNullable = nullability?.get(idx) == true
                    Pair("p$idx", createApiTypeUsage(typeIndex, p.className, isNullable, emptyList(), emptyList()))
                }.toMap()
        }
    }

    val returnType: ApiTypeUsage by lazy(NONE) {
        delegate.visibleAnnotations.hasNullable.let { isNullable ->
            visitedSignature?.returnType(typeIndex, isNullable)
                ?: sourceNameOfBinaryName(Type.getReturnType(delegate.desc).className).let {
                    ApiTypeUsage(it, isNullable, typeIndex(it))
                }
        }
    }

    private
    val visitedSignature: MethodSignatureVisitor? by lazy(NONE) {
        delegate.signature?.let { signature ->
            MethodSignatureVisitor().also { visitor -> SignatureReader(signature).accept(visitor) }
        }
    }

    private
    val List<AnnotationNode>?.hasNullable: Boolean
        get() = this?.any { it.desc == "Ljavax/annotation/Nullable;" } ?: false
}


private
val List<AnnotationNode>?.hasDeprecated: Boolean
    get() = this?.any { it.desc == "Ljava/lang/Deprecated;" } ?: false


private
val List<AnnotationNode>?.hasIncubating: Boolean
    get() = this?.any { it.desc == "Lorg/gradle/api/Incubating;" } ?: false


internal
open class ApiTypeUsage(
    val sourceName: String,
    val isNullable: Boolean,
    val type: ApiType?,
    val typeParameters: List<ApiTypeUsage> = emptyList(),
    val bounds: List<ApiTypeUsage> = emptyList()
) {

    val isRaw: Boolean
        get() = typeParameters.isEmpty() && type?.formalTypeParameters?.isEmpty() != false

    override fun toString(): String {
        return "$sourceName$boundsString${typeParameters.toTypeParametersString(type)}$nullabilityString"
    }

    private
    val boundsString: String
        get() = bounds.takeIf { it.isNotEmpty() }?.joinToString(separator = ", ", prefix = " : ") ?: ""

    private
    val nullabilityString
        get() = if (isNullable) "?" else ""
}


internal
fun List<ApiTypeUsage>.toTypeParametersString(type: ApiType? = null, reified: Boolean = false): String =
    when {
        isNotEmpty() -> this
        type?.formalTypeParameters?.isNotEmpty() == true -> "*".repeat(type.formalTypeParameters.size).asIterable().toList()
        else -> emptyList()
    }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = ", ${if (reified) "reified " else ""}", prefix = "<${if (reified) "reified " else ""}", postfix = ">")
        ?: ""


internal
fun Map<String, ApiTypeUsage>.toFunctionParametersString(): String =
    takeIf { it.isNotEmpty() }
        ?.entries
        ?.mapIndexed { index, entry ->
            if (index == size - 1 && entry.value.sourceName == "Array") "vararg ${entry.key}: ${entry.value.typeParameters.single()}"
            else "${entry.key}: ${entry.value}"
        }
        ?.joinToString(separator = ", ")
        ?: ""


private
fun createApiTypeUsage(
    typeIndex: ApiTypeIndex,
    binaryName: String,
    nullable: Boolean,
    typeParameterSignatures: List<TypeSignatureVisitor>,
    boundsSignatures: List<TypeSignatureVisitor> = emptyList()
): ApiTypeUsage =

    sourceNameOfBinaryName(binaryName).let { sourceName ->
        ApiTypeUsage(
            sourceName,
            nullable,
            typeIndex(sourceName),
            typeParameterSignatures.map { createApiTypeUsage(typeIndex, it.binaryName, false, it.typeParameters) },
            boundsSignatures
                .filter { it.binaryName != "java.lang.Object" }
                .map { createApiTypeUsage(typeIndex, it.binaryName, false, it.typeParameters) })
    }


internal
abstract class BaseSignatureVisitor : SignatureVisitor(ASM6) {

    private
    val formalTypeParameters: MutableMap<String, MutableList<TypeSignatureVisitor>> = mutableMapOf()

    private
    var currentFormalTypeParameter: String? = null

    fun formalTypeParameterDeclarations(typeIndex: ApiTypeIndex): List<ApiTypeUsage> =
        formalTypeParameters.map { (binaryName, boundsSignatures) ->
            createApiTypeUsage(typeIndex, binaryName, false, emptyList(), boundsSignatures)
        }

    override fun visitFormalTypeParameter(name: String) {
        formalTypeParameters[name] = mutableListOf()
        currentFormalTypeParameter = name
    }

    override fun visitClassBound(): SignatureVisitor {
        return TypeSignatureVisitor().also { formalTypeParameters[currentFormalTypeParameter]!!.add(it) }
    }

    override fun visitInterfaceBound(): SignatureVisitor {
        return TypeSignatureVisitor().also { formalTypeParameters[currentFormalTypeParameter]!!.add(it) }
    }
}


internal
class ClassSignatureVisitor : BaseSignatureVisitor()


internal
class MethodSignatureVisitor : BaseSignatureVisitor() {

    private
    val parametersSignatures = mutableListOf<TypeSignatureVisitor>()

    private
    val returnSignature = TypeSignatureVisitor()

    fun parameters(typeIndex: ApiTypeIndex, nullability: List<Boolean>?): Map<String, ApiTypeUsage> =
        parametersSignatures.mapIndexed { idx, parameterSignature ->
            val isNullable = nullability?.get(idx) == true
            Pair(
                "p$idx",
                createApiTypeUsage(typeIndex, parameterSignature.binaryName, isNullable, parameterSignature.typeParameters))
        }.toMap()

    fun returnType(typeIndex: ApiTypeIndex, nullableReturn: Boolean): ApiTypeUsage =
        createApiTypeUsage(
            typeIndex,
            returnSignature.binaryName,
            nullableReturn,
            returnSignature.typeParameters)

    override fun visitParameterType(): SignatureVisitor {
        return TypeSignatureVisitor().also { parametersSignatures.add(it) }
    }

    override fun visitReturnType(): SignatureVisitor {
        return returnSignature
    }
}


internal
class TypeSignatureVisitor : SignatureVisitor(ASM6) {

    lateinit var binaryName: String

    val typeParameters = mutableListOf<TypeSignatureVisitor>()

    private
    var expectingTypeParameter = false

    override fun visitBaseType(descriptor: Char) {
        visitBinaryName(binaryNameForBaseType(descriptor))
    }

    override fun visitArrayType(): SignatureVisitor {
        visitBinaryName("Array")
        return TypeSignatureVisitor().also { typeParameters.add(it) }
    }

    override fun visitClassType(name: String) {
        visitBinaryName(binaryNameOfInternalName(name))
    }

    override fun visitInnerClassType(localName: String) {
        binaryName += "${'$'}$localName"
    }

    override fun visitTypeArgument() {
        expectingTypeParameter = true
    }

    override fun visitTypeArgument(wildcard: Char): SignatureVisitor {
        expectingTypeParameter = true
        return TypeSignatureVisitor().also { typeParameters.add(it) }
    }

    override fun visitTypeVariable(name: String) {
        visitBinaryName(binaryNameOfInternalName(name))
    }

    private
    fun visitBinaryName(binaryName: String) {
        if (expectingTypeParameter) {
            typeParameters.add(TypeSignatureVisitor().also { SignatureReader(binaryName).accept(it) })
            expectingTypeParameter = false
        } else {
            this.binaryName = binaryName
        }
    }
}


private
fun binaryNameForBaseType(descriptor: Char) =
    when (Type.getType(descriptor.toString())) {
        Type.BOOLEAN_TYPE -> Type.BOOLEAN_TYPE.className
        Type.BYTE_TYPE -> Type.BYTE_TYPE.className
        Type.SHORT_TYPE -> Type.SHORT_TYPE.className
        Type.INT_TYPE -> Type.INT_TYPE.className
        Type.CHAR_TYPE -> Type.CHAR_TYPE.className
        Type.LONG_TYPE -> Type.LONG_TYPE.className
        Type.FLOAT_TYPE -> Type.FLOAT_TYPE.className
        Type.DOUBLE_TYPE -> Type.DOUBLE_TYPE.className
        else -> Type.VOID_TYPE.className
    }


private
fun binaryNameOfInternalName(internalName: String): String =
    Type.getObjectType(internalName).className


private
fun sourceNameOfBinaryName(binaryName: String): String =
    when (binaryName) {
        "void" -> "Unit"
        in primitiveTypeStrings.keys -> primitiveTypeStrings[binaryName]!!
        else -> binaryName.replace('$', '.')
    }
