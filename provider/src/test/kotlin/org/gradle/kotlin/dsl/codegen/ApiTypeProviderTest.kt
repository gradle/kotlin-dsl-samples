package org.gradle.kotlin.dsl.codegen

import org.gradle.api.Plugin
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.PluginCollection

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.support.classPathBytecodeRepositoryFor

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

        ApiTypeProvider(classPathBytecodeRepositoryFor(jars)).use { api ->

            assertThat(api.type(Test::class.java.canonicalName), nullValue())

            api.type(PluginCollection::class.java.canonicalName)!!.apply {

                assertThat(sourceName, equalTo("org.gradle.api.plugins.PluginCollection"))
                assertTrue(isPublic)
                assertThat(formalTypeParameters.size, equalTo(1))
                formalTypeParameters.single().apply {
                    assertThat(sourceName, equalTo("T"))
                    assertThat(bounds.size, equalTo(1))
                    assertThat(bounds.single().sourceName, equalTo("org.gradle.api.Plugin"))
                }

                functions.single { it.name == "withType" }.apply {
                    assertThat(formalTypeParameters.size, equalTo(1))
                    formalTypeParameters.single().apply {
                        assertThat(sourceName, equalTo("S"))
                        assertThat(bounds.size, equalTo(1))
                        assertThat(bounds.single().sourceName, equalTo("T"))
                    }
                    assertThat(parameters.size, equalTo(1))
                    parameters.values.single().apply {
                        assertThat(sourceName, equalTo("java.lang.Class"))
                        assertThat(typeParameters.size, equalTo(1))
                        typeParameters.single().apply {
                            assertThat(sourceName, equalTo("S"))
                        }
                    }
                    returnType.apply {
                        assertThat(sourceName, equalTo("org.gradle.api.plugins.PluginCollection"))
                        assertThat(typeParameters.size, equalTo(1))
                        typeParameters.single().apply {
                            assertThat(sourceName, equalTo("S"))
                        }
                    }
                }
            }
            api.type(ObjectFactory::class.java.canonicalName)!!.apply {
                functions.single { it.name == "newInstance" }.apply {
                    parameters.values.drop(1).single().apply {
                        assertThat(sourceName, equalTo("Array"))
                        assertThat(typeParameters.single().sourceName, equalTo("Any"))
                    }
                }
            }
        }
    }
}
