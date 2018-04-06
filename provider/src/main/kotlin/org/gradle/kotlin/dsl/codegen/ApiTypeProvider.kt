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
import org.gradle.kotlin.dsl.support.classPathBytecodeRepositoryFor

import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.Attribute
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassReader.SKIP_CODE
import org.jetbrains.org.objectweb.asm.ClassReader.SKIP_DEBUG
import org.jetbrains.org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_STATIC
import org.jetbrains.org.objectweb.asm.Opcodes.ASM6
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.TypePath
import org.jetbrains.org.objectweb.asm.signature.SignatureReader
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor
import org.jetbrains.org.objectweb.asm.tree.AnnotationNode
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode

import java.io.Closeable
import java.io.File

import kotlin.LazyThreadSafetyMode.NONE


internal
fun apiTypeProviderFor(jarsOrDirs: List<File>): ApiTypeProvider =
    ApiTypeProvider(classPathBytecodeRepositoryFor(jarsOrDirs))


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

    fun type(sourceName: String): ApiType? = open {
        apiTypesBySourceName.computeIfAbsent(sourceName) {
            repository.classBytesFor(sourceName)?.let { apiTypeFor(sourceName, it) }
        }?.invoke()
    }

    fun allTypes(): Sequence<ApiType> = open {
        repository.allClassesBytesBySourceName().mapNotNull { (sourceName, classBytes) ->
            apiTypesBySourceName.computeIfAbsent(sourceName) {
                apiTypeFor(sourceName, classBytes())
            }
        }.map { it() }
    }

    private
    fun apiTypeFor(sourceName: String, classBytes: ByteArray) = {
        ApiType(sourceName, classNodeFor(classBytes), { type(it) })
    }

    private
    fun classNodeFor(classBytes: ByteArray) =
        ApiTypeClassNode().also {
            ClassReader(classBytes).accept(it, SKIP_DEBUG or SKIP_CODE or SKIP_FRAMES)
        }

    private
    fun <T> open(action: () -> T): T =
        if (closed) throw IllegalStateException("ApiTypeProvider closed!")
        else action()
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
        visitedSignature?.formalTypeParameterUsages(typeIndex) ?: emptyList()
    }

    val functions: List<ApiFunction> by lazy(NONE) {
        delegate.methods.filter { it.signature != null }.map { ApiFunction(this, it, typeIndex) }
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
        visitedSignature?.formalTypeParameterUsages(typeIndex)
            ?: emptyList()
    }

    val parameters: List<ApiFunctionParameter> by lazy(NONE) {
        delegate.visibleParameterAnnotations?.map { it.hasNullable }.let { parametersNullability ->
            visitedSignature?.functionParameters(typeIndex, parametersNullability)
                ?: Type.getArgumentTypes(delegate.desc).mapIndexed { idx, p ->
                    val isNullable = parametersNullability?.get(idx) == true
                    apiFunctionParameter(idx, typeIndex.apiTypeUsage(p.className, isNullable))
                }
        }
    }

    val returnType: ApiTypeUsage by lazy(NONE) {
        delegate.visibleAnnotations.hasNullable.let { isNullable ->
            visitedSignature?.returnType(typeIndex, isNullable)
                ?: typeIndex.apiTypeUsage(Type.getReturnType(delegate.desc).className, isNullable)
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


private
fun apiFunctionParameter(index: Int, type: ApiTypeUsage) =
    ApiFunctionParameter("p$index", type)


internal
class ApiFunctionParameter(val name: String, val type: ApiTypeUsage)


internal
class ApiTypeUsage(
    val sourceName: String,
    val isNullable: Boolean = false,
    val type: ApiType? = null,
    val typeParameters: List<ApiTypeUsage> = emptyList(),
    val bounds: List<ApiTypeUsage> = emptyList()
) {
    val isRaw: Boolean
        get() = typeParameters.isEmpty() && type?.formalTypeParameters?.isEmpty() != false
}


private
fun ApiTypeIndex.apiTypeUsage(
    binaryName: String,
    nullable: Boolean,
    typeParameterSignatures: List<TypeSignatureVisitor> = emptyList(),
    boundsSignatures: List<TypeSignatureVisitor> = emptyList()
): ApiTypeUsage =

    sourceNameOfBinaryName(binaryName).let { sourceName ->
        ApiTypeUsage(
            sourceName,
            nullable,
            this(sourceName),
            typeParameterSignatures.map { apiTypeUsage(it.binaryName, false, it.typeParameters) },
            boundsSignatures.map { apiTypeUsage(it.binaryName, false, it.typeParameters) })
    }


internal
abstract class BaseSignatureVisitor : SignatureVisitor(ASM6) {

    private
    val formalTypeParameters = LinkedHashMap<String, MutableList<TypeSignatureVisitor>>(1)

    private
    var currentFormalTypeParameter: String? = null

    fun formalTypeParameterUsages(typeIndex: ApiTypeIndex): List<ApiTypeUsage> =
        formalTypeParameters.map { (binaryName, boundsSignatures) ->
            typeIndex.apiTypeUsage(binaryName, false, emptyList(), boundsSignatures)
        }

    override fun visitFormalTypeParameter(binaryName: String) {
        formalTypeParameters[binaryName] = ArrayList(1)
        currentFormalTypeParameter = binaryName
    }

    override fun visitClassBound(): SignatureVisitor =
        TypeSignatureVisitor().also { formalTypeParameters[currentFormalTypeParameter]!!.add(it) }

    override fun visitInterfaceBound(): SignatureVisitor =
        TypeSignatureVisitor().also { formalTypeParameters[currentFormalTypeParameter]!!.add(it) }
}


internal
class ClassSignatureVisitor : BaseSignatureVisitor()


internal
class MethodSignatureVisitor : BaseSignatureVisitor() {

    private
    val parametersSignatures = ArrayList<TypeSignatureVisitor>(1)

    private
    val returnSignature = TypeSignatureVisitor()

    fun functionParameters(typeIndex: ApiTypeIndex, parametersNullability: List<Boolean>?): List<ApiFunctionParameter> =
        parametersSignatures.mapIndexed { idx, parameterSignature ->
            val isNullable = parametersNullability?.get(idx) == true
            apiFunctionParameter(
                idx,
                typeIndex.apiTypeUsage(parameterSignature.binaryName, isNullable, parameterSignature.typeParameters))
        }

    fun returnType(typeIndex: ApiTypeIndex, nullableReturn: Boolean): ApiTypeUsage =
        typeIndex.apiTypeUsage(returnSignature.binaryName, nullableReturn, returnSignature.typeParameters)

    override fun visitParameterType(): SignatureVisitor =
        TypeSignatureVisitor().also { parametersSignatures.add(it) }

    override fun visitReturnType(): SignatureVisitor =
        returnSignature
}


internal
class TypeSignatureVisitor : SignatureVisitor(ASM6) {

    lateinit var binaryName: String

    val typeParameters = ArrayList<TypeSignatureVisitor>(1)

    private
    var expectingTypeParameter = false

    override fun visitBaseType(descriptor: Char) =
        visitBinaryName(binaryNameForBaseType(descriptor))

    override fun visitArrayType(): SignatureVisitor =
        TypeSignatureVisitor().also {
            visitBinaryName("Array")
            typeParameters.add(it)
        }

    override fun visitClassType(internalName: String) =
        visitBinaryName(binaryNameOfInternalName(internalName))

    override fun visitInnerClassType(localName: String) {
        binaryName += "${'$'}$localName"
    }

    override fun visitTypeArgument() {
        typeParameters.add(TypeSignatureVisitor().also { it.binaryName = "?" })
    }

    override fun visitTypeArgument(wildcard: Char): SignatureVisitor =
        TypeSignatureVisitor().also {
            expectingTypeParameter = true
            typeParameters.add(it)
        }

    override fun visitTypeVariable(internalName: String) {
        visitBinaryName(binaryNameOfInternalName(internalName))
    }

    private
    fun visitBinaryName(binaryName: String) {
        if (expectingTypeParameter) {
            TypeSignatureVisitor().let {
                typeParameters.add(it)
                SignatureReader(binaryName).accept(it)
            }
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
        "?" -> "*"
        in collectionTypeStrings.keys -> collectionTypeStrings[binaryName]!!
        in primitiveTypeStrings.keys -> primitiveTypeStrings[binaryName]!!
        else -> binaryName.replace('$', '.')
    }

private
val collectionTypeStrings =
    mapOf(
        "java.util.Iterable" to "kotlin.collections.Iterable",
        "java.util.Iterator" to "kotlin.collections.Iterator",
        "java.util.Collection" to "kotlin.collections.Collection",
        "java.util.List" to "kotlin.collections.List",
        "java.util.ArrayList" to "kotlin.collections.ArrayList",
        "java.util.Set" to "kotlin.collections.Set",
        "java.util.HashSet" to "kotlin.collections.HashSet",
        "java.util.LinkedHashSet" to "kotlin.collections.LinkedHashSet",
        "java.util.Map" to "kotlin.collections.Map",
        "java.util.HashMap" to "kotlin.collections.HashMap",
        "java.util.LinkedHashMap" to "kotlin.collections.LinkedHashMap")


internal
class ApiTypeClassNode : ClassNode(ASM6) {

    override fun visitSource(file: String?, debug: String?) = Unit
    override fun visitOuterClass(owner: String?, name: String?, desc: String?) = Unit
    override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, visible: Boolean): AnnotationVisitor? = null
    override fun visitAttribute(attr: Attribute?) = Unit
    override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) = Unit
    override fun visitField(access: Int, name: String?, desc: String?, signature: String?, value: Any?): FieldVisitor? = null
}
