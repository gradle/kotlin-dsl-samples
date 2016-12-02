/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package codegen

import groovy.json.JsonSlurper

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

import java.io.File

open class GeneratePluginPortalExtensions : DefaultTask() {

    @get:InputFile
    var jsonlIndexFile: File? = null

    @get:OutputFile
    val outputFile: File
        get() = project.file("src/main/kotlin/${packageName.replace('.', '/')}/PluginPortalExtensions.kt")

    @TaskAction
    fun generate() {
        outputFile.parentedBufferedWriter().use { writer ->
            writer.write(getFileHeader())
            jsonlIndexFile!!.forEachLine {
                val plugin = pluginDataFrom(it)
                writer.write("\n")
                writer.write(pluginExtensionFor(plugin))
                writer.write("\n")
            }
        }
    }

    private val packageName = "org.gradle.script.lang.kotlin"

    private fun getFileHeader(): String = """/*
* Copyright 2016 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package $packageName

import ${PluginDependenciesSpec::class.qualifiedName}
import ${PluginDependencySpec::class.qualifiedName}
"""

    private fun pluginDataFrom(json: String): PluginData =
        JsonSlurper().parseText(json).let { it as Map<*, *> }.run {
            @Suppress("unchecked_cast")
            PluginData(
                id = get("id") as String,
                description = get("description") as String,
                website = get("website") as String,
                versions = get("versions") as List<String>)
        }

    fun pluginExtensionFor(data: PluginData): String =
        kdocFor(data) + "\n" + memberDeclarationFor(data)

    private fun kdocFor(data: PluginData): String =
        """
        /**
         * ${data.description}
         *
         * Visit the [plugin website](${data.website}) for more information.
         */
        """.replaceIndent()

    private fun memberDeclarationFor(plugin: PluginData): String =
        """
        inline val $extendedType.`${memberNameFor(plugin.id)}`: $extensionType
            get() = id("${plugin.id}")
        """.replaceIndent()

    val extendedType = PluginDependenciesSpec::class.simpleName

    val extensionType = PluginDependencySpec::class.simpleName

    fun memberNameFor(pluginId: String) =
        // Since the regular full-stop dot is not a valid member name character in Kotlin,
        // the ONE DOT LEADER character ('\u2024') is being used as a replacement here.
        pluginId.replace('.', '․')
}
