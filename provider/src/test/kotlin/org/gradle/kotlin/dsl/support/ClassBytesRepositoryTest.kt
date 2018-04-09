package org.gradle.kotlin.dsl.support

import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.api.tasks.wrapper.Wrapper

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.DeepThought

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.notNullValue

import org.junit.Assert.assertThat
import org.junit.Test


class ClassBytesRepositoryTest : AbstractIntegrationTest() {

    @Test
    fun `class file path candidates for source name`() {

        assertThat(
            classFilePathCandidatesFor("My").toList(),
            equalTo(listOf("My.class", "MyKt.class")))

        assertThat(
            classFilePathCandidatesFor("foo.My").toList(),
            equalTo(listOf(
                "foo/My.class", "foo/MyKt.class",
                "foo${'$'}My.class", "foo${'$'}MyKt.class")))

        assertThat(
            classFilePathCandidatesFor("foo.My.Nested").toList(),
            equalTo(listOf(
                "foo/My/Nested.class", "foo/My/NestedKt.class",
                "foo/My${'$'}Nested.class", "foo/My${'$'}NestedKt.class",
                "foo${'$'}My${'$'}Nested.class", "foo${'$'}My${'$'}NestedKt.class")))
    }

    @Test
    fun `source name for class file path`() {

        assertThat(kotlinSourceNameOf("My.class"), equalTo("My"))
        assertThat(kotlinSourceNameOf("MyKt.class"), equalTo("My"))

        assertThat(kotlinSourceNameOf("foo/My.class"), equalTo("foo.My"))
        assertThat(kotlinSourceNameOf("foo/MyKt.class"), equalTo("foo.My"))

        assertThat(kotlinSourceNameOf("foo/My${'$'}Nested.class"), equalTo("foo.My.Nested"))
        assertThat(kotlinSourceNameOf("foo/My${'$'}NestedKt.class"), equalTo("foo.My.Nested"))
    }

    class SomeKotlin {
        interface NestedType
    }

    @Test
    fun `finds top-level, nested, java, kotlin types in JARs and directories`() {

        val jar1 = withClassJar(
            "first.jar",
            Groovydoc::class.java,
            Groovydoc.Link::class.java,
            DeepThought::class.java)

        val jar2 = withClassJar(
            "second.jar",
            Wrapper::class.java,
            Wrapper.DistributionType::class.java,
            SomeKotlin::class.java,
            SomeKotlin.NestedType::class.java)

        val cpDir = existing("cp-dir").also { it.mkdirs() }
        unzipTo(cpDir, jar2)

        classPathBytesRepositoryFor(listOf(jar1, cpDir)).use { repo ->
            assertThat(
                repo.classBytesFor(Groovydoc.Link::class.java.canonicalName),
                notNullValue())
            assertThat(
                repo.classBytesFor(Wrapper.DistributionType::class.java.canonicalName),
                notNullValue())
        }

        classPathBytesRepositoryFor(listOf(jar1, cpDir)).use { repo ->
            assertThat(
                repo.allClassesBytesBySourceName().map { it.first }.toList(),
                hasItems(
                    Groovydoc::class.java.canonicalName,
                    Groovydoc.Link::class.java.canonicalName,
                    DeepThought::class.java.canonicalName,
                    Wrapper.DistributionType::class.java.canonicalName,
                    Wrapper::class.java.canonicalName,
                    SomeKotlin.NestedType::class.java.canonicalName,
                    SomeKotlin::class.java.canonicalName))
        }
    }
}
