package org.gradle.script.lang.kotlin.integration

import org.gradle.script.lang.kotlin.KotlinBuildScript
import org.gradle.script.lang.kotlin.provider.KotlinScriptTemplateClassPathModel

import org.gradle.script.lang.kotlin.fixtures.AbstractIntegrationTest
import org.gradle.script.lang.kotlin.fixtures.customInstallation
import org.gradle.script.lang.kotlin.fixtures.withDaemonRegistry

import org.gradle.tooling.GradleConnector

import org.junit.Test

import java.io.File
import java.net.URLClassLoader


class KotlinScriptTemplateClassPathModelIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `can load script template using classpath model`() {

        withBuildScript("")

        val model = fetchKotlinScriptTemplateClassPathModelFor(projectRoot)

        loadScriptTemplateClass(model.classPath, KotlinBuildScript::class.qualifiedName!!)
    }


    private
    fun fetchKotlinScriptTemplateClassPathModelFor(projectDir: File) =
        withDaemonRegistry(File("build/custom/daemon-registry")) {
            val connection = GradleConnector.newConnector()
                .forProjectDirectory(projectDir)
                .useGradleUserHomeDir(File(projectDir, "gradle-user-home"))
                .useInstallation(customInstallation())
                .connect()
            try {
                connection.getModel(KotlinScriptTemplateClassPathModel::class.java)
            } finally {
                connection.close()
            }
        }


    private
    fun loadScriptTemplateClass(classPath: List<File>, scriptTemplateClassName: String) {
        val loader = URLClassLoader(classPath.map { it.toURI().toURL() }.toTypedArray())
        try {
            loader.loadClass(scriptTemplateClassName)
        } finally {
            loader.close()
        }
    }
}
