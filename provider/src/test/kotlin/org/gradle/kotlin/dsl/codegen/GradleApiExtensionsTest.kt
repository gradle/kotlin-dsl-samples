package org.gradle.kotlin.dsl.codegen

import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
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

import org.hamcrest.CoreMatchers.containsString
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
        val jars = listOf(withClassJar(
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

        val generatedExtensions = apiTypeProviderFor(jars).use { api ->
            gradleApiExtensionDeclarationsFor(api).toList()
        }

        assertThat(generatedExtensions.filter { it.contains("KClass<") }.size, equalTo(14))

        generatedExtensions.apply {

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : org.gradle.api.Named> org.gradle.api.model.ObjectFactory.named(p0: kotlin.reflect.KClass<T>, p1: String): T =
                named(p0.java, p1)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.model.ObjectFactory.newInstance(p0: kotlin.reflect.KClass<T>, vararg p1: Any): T =
                newInstance(p0.java, p1)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.model.ObjectFactory.property(p0: kotlin.reflect.KClass<T>): org.gradle.api.provider.Property<T> =
                property(p0.java)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.model.ObjectFactory.listProperty(p0: kotlin.reflect.KClass<T>): org.gradle.api.provider.ListProperty<T> =
                listProperty(p0.java)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.model.ObjectFactory.setProperty(p0: kotlin.reflect.KClass<T>): org.gradle.api.provider.SetProperty<T> =
                setProperty(p0.java)
            """)

            assertContainsExtension("""
            fun <S : T, T : org.gradle.api.Plugin<*>> org.gradle.api.plugins.PluginCollection<T>.withType(p0: kotlin.reflect.KClass<S>): org.gradle.api.plugins.PluginCollection<S> =
                withType(p0.java)
            """)

            assertContainsExtension("""
            @Deprecated("Deprecated Gradle API")
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.provider.ProviderFactory.property(p0: kotlin.reflect.KClass<T>): org.gradle.api.provider.PropertyState<T> =
                property(p0.java)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.plugins.ExtensionContainer.add(p0: kotlin.reflect.KClass<T>, p1: String, p2: T): Unit =
                add(p0.java, p1, p2)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.plugins.ExtensionContainer.create(p0: kotlin.reflect.KClass<T>, p1: String, p2: kotlin.reflect.KClass<T>, vararg p3: Any): T =
                create(p0.java, p1, p2.java, p3)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.plugins.ExtensionContainer.create(p0: org.gradle.api.reflect.TypeOf<T>, p1: String, p2: kotlin.reflect.KClass<T>, vararg p3: Any): T =
                create(p0, p1, p2.java, p3)
            """)

            assertContainsExtension("""
            fun <T : Any> org.gradle.api.plugins.ExtensionContainer.getByType(p0: kotlin.reflect.KClass<T>): T =
                getByType(p0.java)
            """)

            assertContainsExtension("""
            fun <T : Any> org.gradle.api.plugins.ExtensionContainer.findByType(p0: kotlin.reflect.KClass<T>): T? =
                findByType(p0.java)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            fun <T : Any> org.gradle.api.plugins.ExtensionContainer.configure(p0: kotlin.reflect.KClass<T>, p1: T.() -> Unit): Unit =
                configure(p0.java, p1)
            """)
        }
    }

    @Test
    fun `reified type extensions`() {

        val jars = listOf(withClassJar(
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

        val generatedExtensions = apiTypeProviderFor(jars).use { api ->
            gradleApiExtensionDeclarationsFor(api).toList()
        }

        assertThat(generatedExtensions.filter { it.contains("<reified ") }.size, equalTo(13))

        generatedExtensions.apply {

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : org.gradle.api.Named> org.gradle.api.model.ObjectFactory.named(p0: String): T =
                named(T::class.java, p0)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : Any> org.gradle.api.model.ObjectFactory.newInstance(vararg p0: Any): T =
                newInstance(T::class.java, p0)
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
            inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.add(p0: String, p1: T): Unit =
                add(typeOf<T>(), p0, p1)
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
            inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.create(p0: String, p1: kotlin.reflect.KClass<T>, vararg p2: Any): T =
                create(typeOf<T>(), p0, p1.java, p2)
            """)

            assertContainsExtension("""
            inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.create(p0: String, vararg p1: Any): T =
                create(p0, T::class.java, p1)
            """)

            assertContainsExtension("""
            @org.gradle.api.Incubating
            inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.configure(noinline p0: T.() -> Unit): Unit =
                configure(typeOf<T>(), p0)
            """)
        }
    }

    private
    fun List<String>.assertContainsExtension(string: String) {
        assertThat(this, hasItem(containsMultiLineString(string)))
    }

    @Test
    fun `varargs extension parameters`() {
        val jars = listOf(withClassJar("some.jar", ArtifactResolutionQuery::class.java))

        val generatedExtensions = apiTypeProviderFor(jars).use { api ->
            gradleApiExtensionDeclarationsFor(api).toList()
        }

        val varargExtension = generatedExtensions.single { it.contains(".withArtifacts(") && it.contains("vararg ") }

        assertThat(varargExtension,
            containsString("vararg p1: kotlin.reflect.KClass<org.gradle.api.component.Artifact>"))
        assertThat(varargExtension,
            containsString("withArtifacts(p0.java, *p1.map { it.java }.toTypedArray())"))
    }
}
