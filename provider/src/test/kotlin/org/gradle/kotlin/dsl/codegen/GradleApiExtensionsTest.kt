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

        assertThat(generatedExtensions, hasItem(containsString(
            "@org.gradle.api.Incubating\n" +
                "inline fun <reified T : org.gradle.api.Named> org.gradle.api.model.ObjectFactory.named(p1: String): T =")))

        assertThat(generatedExtensions, hasItem(containsString(
            "@org.gradle.api.Incubating\n" +
                "inline fun <reified T : Any> org.gradle.api.model.ObjectFactory.newInstance(vararg p1: Any): T =")))

        assertThat(generatedExtensions, hasItem(containsString(
            "@org.gradle.api.Incubating\n" +
                "inline fun <reified T : Any> org.gradle.api.model.ObjectFactory.property(): org.gradle.api.provider.Property<T> =")))

        assertThat(generatedExtensions, hasItem(containsString(
            "@org.gradle.api.Incubating\n" +
                "inline fun <reified T : Any> org.gradle.api.model.ObjectFactory.listProperty(): org.gradle.api.provider.ListProperty<T> =")))

        assertThat(generatedExtensions, hasItem(containsString(
            "@org.gradle.api.Incubating\n" +
                "inline fun <reified T : Any> org.gradle.api.model.ObjectFactory.setProperty(): org.gradle.api.provider.SetProperty<T> =")))

        assertThat(generatedExtensions, hasItem(containsString(
            "inline fun <reified S : T, T : org.gradle.api.Plugin<*>> org.gradle.api.plugins.PluginCollection<T>.withType(): org.gradle.api.plugins.PluginCollection<S> =")))

        assertThat(generatedExtensions, hasItem(containsString(
            "@Deprecated(\"Deprecated Gradle API\")\n" +
                "@org.gradle.api.Incubating\n" +
                "inline fun <reified T : Any> org.gradle.api.provider.ProviderFactory.property(): org.gradle.api.provider.PropertyState<T> =")))

        assertThat(generatedExtensions, hasItem(containsString(
            "@org.gradle.api.Incubating\n" +
                "inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.add(p1: String, p2: T): Unit =\n" +
                "    add(typeOf<T>(), p1, p2)")))

        assertThat(generatedExtensions, hasItem(containsString(
            "@org.gradle.api.Incubating\n" +
                "inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.getByType(): T =\n" +
                "    getByType(typeOf<T>())")))

        assertThat(generatedExtensions, hasItem(containsString(
            "@org.gradle.api.Incubating\n" +
                "inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.findByType(): T? =\n" +
                "    findByType(typeOf<T>())")))

        assertThat(generatedExtensions, hasItem(containsString(
            "@org.gradle.api.Incubating\n" +
                "inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.create(p1: String, p2: kotlin.reflect.KClass<T>, vararg p3: Any): T =\n" +
                "    create(typeOf<T>(), p1, p2.java, p3)")))

        assertThat(generatedExtensions, hasItem(containsString(
            "inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.create(p0: String, vararg p2: Any): T =\n" +
                "    create(p0, T::class.java, p2)")))

        assertThat(generatedExtensions, hasItem(containsString(
            "@org.gradle.api.Incubating\n" +
                "inline fun <reified T : Any> org.gradle.api.plugins.ExtensionContainer.configure(noinline p1: T.() -> Unit): Unit =\n" +
                "    configure(typeOf<T>(), p1)")))
    }

    @Test
    fun `varargs extension parameters`() {
        val jars = listOf(withClassJar("some.jar", ArtifactResolutionQuery::class.java))

        val generatedExtensions = apiTypeProviderFor(jars).use { api ->
            gradleApiExtensionDeclarationsFor(api).toList()
        }

        val varargExtension = generatedExtensions.single { it.contains(".withArtifacts(") && it.contains("vararg ") }
        println(varargExtension)

        assertThat(varargExtension,
            containsString("vararg p1: kotlin.reflect.KClass<org.gradle.api.component.Artifact>"))
        assertThat(varargExtension,
            containsString("withArtifacts(p0.java, *p1.map { it.java }.toTypedArray())"))
    }
}
