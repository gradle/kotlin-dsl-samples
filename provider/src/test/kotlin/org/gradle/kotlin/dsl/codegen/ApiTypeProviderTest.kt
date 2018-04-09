package org.gradle.kotlin.dsl.codegen

import org.gradle.api.Plugin
import org.gradle.api.file.ContentFilterable
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.PluginCollection
import org.gradle.api.tasks.AbstractCopyTask

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue

import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test


class ApiTypeProviderTest : AbstractIntegrationTest() {

    @Test
    fun `provides a source code generation oriented model over a classpath`() {

        val jars = listOf(withClassJar("some.jar",
            Plugin::class.java,
            PluginCollection::class.java,
            ObjectFactory::class.java))

        apiTypeProviderFor(jars).use { api ->

            assertThat(api.type(Test::class.java.canonicalName), nullValue())

            api.type(PluginCollection::class.java.canonicalName)!!.apply {

                assertThat(sourceName, equalTo("org.gradle.api.plugins.PluginCollection"))
                assertTrue(isPublic)
                assertThat(typeParameters.size, equalTo(1))
                typeParameters.single().apply {
                    assertThat(sourceName, equalTo("T"))
                    assertThat(bounds.size, equalTo(1))
                    assertThat(bounds.single().sourceName, equalTo("org.gradle.api.Plugin"))
                }

                functions.single { it.name == "withType" }.apply {
                    assertThat(typeParameters.size, equalTo(1))
                    typeParameters.single().apply {
                        assertThat(sourceName, equalTo("S"))
                        assertThat(bounds.size, equalTo(1))
                        assertThat(bounds.single().sourceName, equalTo("T"))
                    }
                    assertThat(parameters.size, equalTo(1))
                    parameters.single().type.apply {
                        assertThat(sourceName, equalTo("java.lang.Class"))
                        assertThat(typeArguments.size, equalTo(1))
                        typeArguments.single().apply {
                            assertThat(sourceName, equalTo("S"))
                        }
                    }
                    returnType.apply {
                        assertThat(sourceName, equalTo("org.gradle.api.plugins.PluginCollection"))
                        assertThat(typeArguments.size, equalTo(1))
                        typeArguments.single().apply {
                            assertThat(sourceName, equalTo("S"))
                        }
                    }
                }
            }
            api.type(ObjectFactory::class.java.canonicalName)!!.apply {
                functions.single { it.name == "newInstance" }.apply {
                    parameters.drop(1).single().type.apply {
                        assertThat(sourceName, equalTo("kotlin.Array"))
                        assertThat(typeArguments.single().sourceName, equalTo("Any"))
                    }
                }
            }
        }
    }

    @Test
    fun `maps generic question mark to *`() {

        val jars = listOf(withClassJar("some.jar", ContentFilterable::class.java))

        apiTypeProviderFor(jars).use { api ->
            val contentFilterable = api.type(ContentFilterable::class.java.canonicalName)!!

            contentFilterable.functions.single { it.name == "expand" }.apply {
                assertTrue(typeParameters.isEmpty())
                assertThat(parameters.size, equalTo(1))
                parameters.single().type.apply {
                    assertThat(sourceName, equalTo("kotlin.collections.Map"))
                    assertThat(typeArguments.size, equalTo(2))
                    assertThat(typeArguments[0].sourceName, equalTo("String"))
                    assertThat(typeArguments[1].sourceName, equalTo("*"))
                }
            }
        }
    }

    @Test
    fun `ignores functions overrides`() {
        val jars = listOf(withClassJar("some.jar", AbstractCopyTask::class.java))

        apiTypeProviderFor(jars).use { api ->
            val type = api.type(AbstractCopyTask::class.java.canonicalName)!!

            val filterFunctions = type.functions.filter { it.name == "filter" }
            assertThat(filterFunctions.size, equalTo(3))
        }
    }
}
