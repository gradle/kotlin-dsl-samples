package org.gradle.kotlin.dsl.samples

import org.gradle.samples.test.rule.Sample
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assume.assumeTrue
import org.junit.Rule

import java.io.File
import java.util.Properties


abstract class AbstractSampleTest(sampleDirName: String) {

    @Rule
    @JvmField
    val sample: Sample = Sample.from(samplesDir).intoTemporaryFolder().withDefaultSample(sampleDirName)

    protected
    val projectRoot: File
        get() = sample.dir

    protected
    fun build(vararg arguments: String): BuildResult =
        build(projectRoot, *arguments)

    protected
    fun build(projectDir: File, vararg arguments: String): BuildResult =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(*arguments)
            .build()
            .apply { println(output) }


    protected
    fun existing(path: String): File =
        sample.dir.resolve(path)

    protected
    fun assumeAndroidHomeIsSet() =
        assumeTrue(System.getenv().containsKey("ANDROID_HOME"))
}


private
val testProperties =
    Properties().also { properties ->
        AbstractSampleTest::class.java.getResourceAsStream("/test.properties")?.use { input ->
            properties.load(input)
        }
    }


private
val samplesDir =
    testProperties["samplesDir"].toString()


internal
val samplesDirFile =
    File(samplesDir)
