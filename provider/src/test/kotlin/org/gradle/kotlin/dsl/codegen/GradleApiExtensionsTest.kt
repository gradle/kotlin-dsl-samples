package org.gradle.kotlin.dsl.codegen

import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.PluginCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.customInstallation
import org.gradle.kotlin.dsl.support.classPathBytecodeRepositoryFor

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.hasItem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test


class GradleApiExtensionsTest : AbstractIntegrationTest() {

    @Test
    fun `Gradle API`() {

        val gradleJars = customInstallation().let { listOf(it.resolve("lib"), it.resolve("lib/plugins")) }.flatMap { it.listFiles().toList() }

        ApiTypeProvider(classPathBytecodeRepositoryFor(gradleJars)).use { api ->

            var seenPrivateType = false
            var seenInternalType = false
            var seenPublicType = false

            var seenPrivateFunction = false
            var seenPublicFunction = false

            api.allTypes().filter { it.isGradleApi }.forEach {
                if (it.sourceName == "org.gradle.api.Project") seenPublicType = true
                if (!it.isPublic) seenPrivateType = true
                if (it.sourceName.startsWith("org.gradle.util") || it.sourceName.contains(".internal.")) seenInternalType = true

                it.gradleApiFunctions.forEach {
                    if (it.isPublic) seenPublicFunction = true
                    else seenPrivateFunction = true
                }
            }

            assertFalse(seenPrivateType)
            assertFalse(seenInternalType)
            assertTrue(seenPublicType)

            assertFalse(seenPrivateFunction)
            assertTrue(seenPublicFunction)
        }
    }

    @Test
    fun `reified type extensions`() {

        val jar = withClassJar(
            "unbounded.jar",
            Named::class.java,
            Property::class.java,
            ListProperty::class.java,
            SetProperty::class.java,
            Plugin::class.java,
            ObjectFactory::class.java,
            PluginCollection::class.java)

        ApiTypeProvider(classPathBytecodeRepositoryFor(listOf(jar))).use { api ->

            assertThat(
                gradleApiExtensionDeclarationsFor(api).toList(),
                allOf(
                    hasItem(containsString(
                        "inline fun <reified T : org.gradle.api.Named> org.gradle.api.model.ObjectFactory.named(p1: String): T =")),
                    hasItem(containsString(
                        "inline fun <reified T> org.gradle.api.model.ObjectFactory.property(): org.gradle.api.provider.Property<T> =")),
                    hasItem(containsString(
                        "inline fun <reified T> org.gradle.api.model.ObjectFactory.listProperty(): org.gradle.api.provider.ListProperty<T> =")),
                    hasItem(containsString(
                        "inline fun <reified T> org.gradle.api.model.ObjectFactory.setProperty(): org.gradle.api.provider.SetProperty<T> =")),
                    hasItem(containsString(
                        "inline fun <reified S : T, T : org.gradle.api.Plugin<*>> org.gradle.api.plugins.PluginCollection<T>.withType(): org.gradle.api.plugins.PluginCollection<S> ="))
                ))
        }
    }
}
