package org.gradle.kotlin.dsl.codegen

import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.PluginCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.util.TextUtil

import org.gradle.kotlin.dsl.GradleDsl
import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.support.classPathBytecodeRepositoryFor

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.startsWith

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test


class GradleApiExtensionsTest : AbstractIntegrationTest() {

    @Test
    fun `Gradle API spec`() {

        val jars = listOf(withClassJar(
            "some.jar",
            Project::class.java,
            ProjectInternal::class.java,
            TextUtil::class.java,
            GradleDsl::class.java))

        ApiTypeProvider(classPathBytecodeRepositoryFor(jars)).use { api ->

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
            Project::class.java))

        val generatedExtensions = ApiTypeProvider(classPathBytecodeRepositoryFor(jars)).use { api ->
            gradleApiExtensionDeclarationsFor(api).toList()
        }

        assertThat(generatedExtensions.size, equalTo(9))

        assertThat(
            generatedExtensions,
            hasItems(
                startsWith(
                    "inline fun <reified T : org.gradle.api.Named> org.gradle.api.model.ObjectFactory.named(p1: String): T ="),
                startsWith(
                    "inline fun <reified T> org.gradle.api.model.ObjectFactory.newInstance(vararg p1: Any): T ="),
                startsWith(
                    "inline fun <reified T> org.gradle.api.model.ObjectFactory.property(): org.gradle.api.provider.Property<T> ="),
                startsWith(
                    "inline fun <reified T> org.gradle.api.model.ObjectFactory.listProperty(): org.gradle.api.provider.ListProperty<T> ="),
                startsWith(
                    "inline fun <reified T> org.gradle.api.model.ObjectFactory.setProperty(): org.gradle.api.provider.SetProperty<T> ="),
                startsWith(
                    "inline fun <reified S : T, T : org.gradle.api.Plugin<*>> org.gradle.api.plugins.PluginCollection<T>.withType(): org.gradle.api.plugins.PluginCollection<S> ="),
                startsWith(
                    "@Deprecated(\"Deprecated Gradle API\")\ninline fun <reified T> org.gradle.api.Project.property(): org.gradle.api.provider.PropertyState<T> ="),
                startsWith(
                    "inline fun <reified T> org.gradle.api.Project.container(): org.gradle.api.NamedDomainObjectContainer<T> ="),
                startsWith(
                    "inline fun <reified T> org.gradle.api.Project.container(p1: org.gradle.api.NamedDomainObjectFactory<T>): org.gradle.api.NamedDomainObjectContainer<T> =")))
    }
}
