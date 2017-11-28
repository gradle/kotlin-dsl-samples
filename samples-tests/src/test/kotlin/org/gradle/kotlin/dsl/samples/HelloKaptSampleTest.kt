package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class HelloKaptSampleTest : AbstractSampleTest("hello-kapt") {

    @Test
    fun `hello kapt`() {
        val output = build("run").output
        println(output)
        assertThat(
            output,
            containsString("Hello User{name=Douglas, age=42}")
        )
    }

    @Test
    fun `kapt javac options and kapt arguments`() {
        existing("build.gradle.kts")
            .appendText("""
                logger.info("JavacOptions: " + kapt.getJavacOptions().toString())
            """)

        val output = build("run", "--info").output

        assertThat(
            output,
            allOf(
                containsString("The following options were not recognized by any processor: '[SomeKaptArgument"),
                containsString("JavacOptions: {SomeJavacOption=OptionValue}")
            )
        )
    }
}
