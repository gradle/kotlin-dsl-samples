package org.gradle.kotlin.dsl.codegen

import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
import org.gradle.api.component.Artifact
import org.gradle.api.component.Component
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.PluginCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.SetProperty
import org.gradle.util.TextUtil

import org.gradle.kotlin.dsl.GradleDsl
import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString
import org.gradle.kotlin.dsl.fixtures.customInstallation

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test

import java.io.File
import java.io.FileFilter

import kotlin.system.measureTimeMillis


class GradleApiExtensionsTest : AbstractIntegrationTest() {

    private
    val gradleApiParameterNamesJar: File by lazy {
        customInstallation().resolve("lib").listFiles(FileFilter { it.name.startsWith("gradle-api-parameter-names-") }).single()
    }

    @Test
    fun `whole Gradle API extensions generation and compilation`() {
        val jars = customInstallation().let { custom ->
            sequenceOf(custom.resolve("lib"), custom.resolve("lib/plugins")).flatMap {
                it.listFiles(FileFilter { it.name.endsWith(".jar") }).asSequence()
            }.toList()
        }

        val sourceFile: File = existing("source.kt")

        measureTimeMillis {
            writeGradleApiExtensionsTo(sourceFile, jars)
        }.also {
            println("Generation to file succeeded in ${it}ms")
        }

        try {
            measureTimeMillis {
                StandardKotlinFileCompiler.compileToDirectory(
                    existing("output").also { it.mkdirs() },
                    listOf(sourceFile),
                    jars)
            }.also {
                println("Compilation succeeded in ${it}ms")
            }
        } catch (ex: Exception) {
            sourceFile.readLines().mapIndexed { idx, line -> "${(idx + 1).toString().padStart(4)}: $line" }.forEach { println(it) }
            throw ex
        }
    }

    @Test
    fun `Gradle API spec`() {

        val jars = listOf(withClassJar(
            "some.jar",
            Project::class.java,
            ProjectInternal::class.java,
            TextUtil::class.java,
            GradleDsl::class.java))

        apiTypeProviderFor(jars).use { api ->

            var seenProject = false
            var seenProjectInternal = false
            var seenTextUtil = false
            var seenGradleDsl = false

            api.allTypes().filter { it.isGradleApi }.forEach {
                when (it.sourceName) {
                    Project::class.java.canonicalName -> seenProject = true
                    ProjectInternal::class.java.canonicalName -> seenProjectInternal = true
                    TextUtil::class.java.canonicalName -> seenTextUtil = true
                    GradleDsl::class.java.canonicalName -> seenGradleDsl = true
                }
            }

            assertTrue(seenProject)
            assertFalse(seenProjectInternal)
            assertFalse(seenTextUtil)
            assertFalse(seenGradleDsl)
        }
    }

    @Test
    fun `class to kclass extensions`() {
        val jars = listOf(gradleApiParameterNamesJar, withClassJar(
            "some.jar",
            Named::class.java,
            Property::class.java,
            ListProperty::class.java,
            SetProperty::class.java,
            Plugin::class.java,
            ObjectFactory::class.java,
            PluginCollection::class.java,
            ProviderFactory::class.java,
            ExtensionContainer::class.java))

        val generatedExtensions = apiTypeProviderFor(jars, parameterNamesSupplierFor(jars)).use { api ->
            gradleApiExtensionDeclarationsFor(api).toList()
        }

        assertThat(generatedExtensions.filter { it.contains("KClass<") }.size, equalTo(14))

        generatedExtensions.apply {

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : org.gradle.api.Named> org.gradle.api.model.ObjectFactory.named(type: kotlin.reflect.KClass<T>, name: String): T =
                named(type.java, name)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.model.ObjectFactory.newInstance(type: kotlin.reflect.KClass<T>, vararg parameters: Any): T =
                newInstance(type.java, parameters)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.model.ObjectFactory.property(valueType: kotlin.reflect.KClass<T>): org.gradle.api.provider.Property<T> =
                property(valueType.java)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.model.ObjectFactory.listProperty(elementType: kotlin.reflect.KClass<T>): org.gradle.api.provider.ListProperty<T> =
                listProperty(elementType.java)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.model.ObjectFactory.setProperty(elementType: kotlin.reflect.KClass<T>): org.gradle.api.provider.SetProperty<T> =
                setProperty(elementType.java)
            """)

            assertContainsExtension("""
            fun <S : T, T : org.gradle.api.Plugin<*>> org.gradle.api.plugins.PluginCollection<T>.withType(type: kotlin.reflect.KClass<S>): org.gradle.api.plugins.PluginCollection<S> =
                withType(type.java)
            """)

            assertContainsExtension("""
            @Deprecated("Deprecated Gradle API")
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.provider.ProviderFactory.property(valueType: kotlin.reflect.KClass<T>): org.gradle.api.provider.PropertyState<T> =
                property(valueType.java)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.plugins.ExtensionContainer.add(publicType: kotlin.reflect.KClass<T>, name: String, extension: T): Unit =
                add(publicType.java, name, extension)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.plugins.ExtensionContainer.create(publicType: kotlin.reflect.KClass<T>, name: String, instanceType: kotlin.reflect.KClass<T>, vararg constructionArguments: Any): T =
                create(publicType.java, name, instanceType.java, constructionArguments)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.plugins.ExtensionContainer.create(publicType: org.gradle.api.reflect.TypeOf<T>, name: String, instanceType: kotlin.reflect.KClass<T>, vararg constructionArguments: Any): T =
                create(publicType, name, instanceType.java, constructionArguments)
            """)

            assertContainsExtension("""
            fun <T : Any> org.gradle.api.plugins.ExtensionContainer.getByType(type: kotlin.reflect.KClass<T>): T =
                getByType(type.java)
            """)

            assertContainsExtension("""
            fun <T : Any> org.gradle.api.plugins.ExtensionContainer.findByType(type: kotlin.reflect.KClass<T>): T? =
                findByType(type.java)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.plugins.ExtensionContainer.configure(type: kotlin.reflect.KClass<T>, action: T.() -> Unit): Unit =
                configure(type.java, action)
            """)
        }
    }

    @Test
    fun `reified type extensions`() {

        val jars = listOf(gradleApiParameterNamesJar, withClassJar(
            "some.jar",
            Named::class.java,
            Property::class.java,
            ListProperty::class.java,
            SetProperty::class.java,
            Plugin::class.java,
            ObjectFactory::class.java,
            PluginCollection::class.java,
            ProviderFactory::class.java,
            ExtensionContainer::class.java))

        val generatedExtensions = apiTypeProviderFor(jars, parameterNamesSupplierFor(jars)).use { api ->
            gradleApiExtensionDeclarationsFor(api).toList()
        }

        assertThat(generatedExtensions.filter { it.contains("<reified ") }.size, equalTo(13))

        generatedExtensions.apply {

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : org.gradle.api.Named> org.gradle.api.model.ObjectFactory.named(name: String): T =
                named(T::class.java, name)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : Any> org.gradle.api.model.ObjectFactory.newInstance(vararg parameters: Any): T =
                newInstance(T::class.java, parameters)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : Any> org.gradle.api.model.ObjectFactory.property(): org.gradle.api.provider.Property<T> =
                property(T::class.java)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : Any> org.gradle.api.model.ObjectFactory.listProperty(): org.gradle.api.provider.ListProperty<T> =
                listProperty(T::class.java)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : Any> org.gradle.api.model.ObjectFactory.setProperty(): org.gradle.api.provider.SetProperty<T> =
                setProperty(T::class.java)
            """)

            assertContainsExtension("""
            inline fun <reified S : T, T : org.gradle.api.Plugin<*>> org.gradle.api.plugins.PluginCollection<T>.withType(): org.gradle.api.plugins.PluginCollection<S> =
                withType(S::class.java)
            """)

            assertContainsExtension("""
            @Deprecated("Deprecated Gradle API")
            @org.gradle.api.Incubating
            inline fun <reified T : Any> org.gradle.api.provider.ProviderFactory.property(): org.gradle.api.provider.PropertyState<T> =
                property(T::class.java)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.add(name: String, extension: T): Unit =
                add(typeOf<T>(), name, extension)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.getByType(): T =
                getByType(typeOf<T>())
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.findByType(): T? =
                findByType(typeOf<T>())
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.create(name: String, instanceType: kotlin.reflect.KClass<T>, vararg constructionArguments: Any): T =
                create(typeOf<T>(), name, instanceType.java, constructionArguments)
            """)

            assertContainsExtension("""
            inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.create(name: String, vararg constructionArguments: Any): T =
                create(name, T::class.java, constructionArguments)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.configure(noinline action: T.() -> Unit): Unit =
                configure(typeOf<T>(), action)
            """)
        }
    }

    private
    fun List<String>.assertContainsExtension(string: String) {
        assertThat(this, hasItem(containsMultiLineString(string)))
    }

    @Test
    fun `varargs and collections of either TypeOf or Class extension parameters`() {

        val jars = listOf(gradleApiParameterNamesJar, withClassJar(
            "some.jar",
            ArtifactResolutionQuery::class.java,
            Component::class.java,
            Artifact::class.java))

        val generatedExtensions = apiTypeProviderFor(jars, parameterNamesSupplierFor(jars)).use { api ->
            gradleApiExtensionDeclarationsFor(api).toList()
        }

        generatedExtensions.filter { it.contains(".withArtifacts(") }.apply {

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : org.gradle.api.component.Component> org.gradle.api.artifacts.query.ArtifactResolutionQuery.withArtifacts(vararg artifactTypes: kotlin.reflect.KClass<org.gradle.api.component.Artifact>): org.gradle.api.artifacts.query.ArtifactResolutionQuery =
                withArtifacts(T::class.java, *artifactTypes.map { it.java }.toTypedArray())
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : org.gradle.api.component.Component> org.gradle.api.artifacts.query.ArtifactResolutionQuery.withArtifacts(artifactTypes: kotlin.collections.Collection<kotlin.reflect.KClass<org.gradle.api.component.Artifact>>): org.gradle.api.artifacts.query.ArtifactResolutionQuery =
                withArtifacts(T::class.java, artifactTypes.map { it.java })
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun org.gradle.api.artifacts.query.ArtifactResolutionQuery.withArtifacts(componentType: kotlin.reflect.KClass<org.gradle.api.component.Component>, vararg artifactTypes: kotlin.reflect.KClass<org.gradle.api.component.Artifact>): org.gradle.api.artifacts.query.ArtifactResolutionQuery =
                withArtifacts(componentType.java, *artifactTypes.map { it.java }.toTypedArray())
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun org.gradle.api.artifacts.query.ArtifactResolutionQuery.withArtifacts(componentType: kotlin.reflect.KClass<org.gradle.api.component.Component>, artifactTypes: kotlin.collections.Collection<kotlin.reflect.KClass<org.gradle.api.component.Artifact>>): org.gradle.api.artifacts.query.ArtifactResolutionQuery =
                withArtifacts(componentType.java, artifactTypes.map { it.java })
            """)

            assertThat(size, equalTo(4))
        }
    }
}
