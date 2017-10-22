package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test

class SourceControlSampleTest : AbstractSampleTest("source-control") {

    @Test
    fun `source dependencies mapping`() {

        assertThat(
            build("run").output,
            containsString("The answer to the ultimate question of Life, the Universe and Everything is 42."))
    }
}
