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
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

import java.io.BufferedWriter
import java.io.File

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors.newCachedThreadPool
import java.util.concurrent.Future

open class GeneratePluginPortalExtensions : DefaultTask() {

    @get:InputFile
    var jsonlIndexFile: File? = null

    @get:OutputDirectory
    val outputDir: File by lazy {
        project.file("src/main/kotlin/${packageName.replace('.', '/')}")
    }

    typealias ActorMap = MutableMap<String, Actor>

    typealias Actor = Pair<EventQ, Future<*>>

    typealias EventQ = BlockingQueue<Event>

    sealed class Event {
        data class OnNext(val plugin: PluginData) : Event()
        object OnCompleted : Event()
    }

    @Suppress("unused")
    @TaskAction
    fun generate() {
        val scheduler = newCachedThreadPool()
        val actorBySegment = mutableMapOf<String, Actor>()
        try {
            pluginDataSortedByPluginId().forEach { plugin ->
                fork(scheduler, actorBySegment, plugin, extendedType = pluginsBlockType, segmentSize = 1)
            }
        } finally {
            requestAndAwaitCompletionOf(actorBySegment.values)
            scheduler.shutdownAndAwaitTermination()
        }
    }

    private fun pluginDataSortedByPluginId() =
        jsonlIndexFile!!.readLines().map { pluginDataFrom(it) }.sortedBy { it.id }

    private fun fork(scheduler: ExecutorService, actorBySegment: ActorMap, plugin: PluginData, extendedType: String, segmentSize: Int) {
        val segment = segmentOf(plugin.id, segmentSize)
        val actor = actorBySegment.getOrPut(segment) {
            scheduler.spawn { eventQ ->
                processSegment(scheduler, eventQ, segment, extendedType, segmentSize)
            }
        }
        postTo(actor, Event.OnNext(plugin))
    }

    private fun processSegment(scheduler: ExecutorService, eventQ: EventQ, segment: String, extendedType: String, segmentSize: Int) {

        val actorBySegment = mutableMapOf<String, Actor>()
        val pluginIdSize = segmentSize + 1
        val pluginIds = mutableSetOf<String>()
        val pluginActor = scheduler.spawn { eventQ ->
            processPluginsOf(segment, extendedType, eventQ)
        }

        tailrec fun processNext() {
            val event = eventQ.take()
            if (event is Event.OnNext) {
                val plugin = event.plugin
                val parts = plugin.id.split('.')
                when (parts.size) {
                    pluginIdSize -> {
                        pluginIds.add(plugin.id)
                        postTo(pluginActor, event)
                    }
                    else -> @Suppress("name_shadowing") {
                        val isBranchingFromPluginId = joinSegmentOf(parts, pluginIdSize) in pluginIds
                        val extendedType =
                            if (isBranchingFromPluginId) pluginIdTypeFor(parts.take(pluginIdSize))
                            else pluginGroupTypeFor(parts.take(segmentSize))
                        fork(scheduler, actorBySegment, plugin, extendedType, segmentSize + 1)
                    }
                }
                processNext()
            }
        }
        try {
            processNext()
        } finally {
            requestAndAwaitCompletionOf(
                listOf(pluginActor) + actorBySegment.values)
        }
    }

    fun processPluginsOf(segment: String, extendedType: String, eventQ: EventQ) {
        val parts = segment.split('.')
        val segmentTypeName = pluginGroupTypeFor(parts)

        fun BufferedWriter.writeFileHeader() {
            appendln(getFileHeader())
            newLine()
            appendCode("""
                class `$segmentTypeName`(internal val plugins: $pluginsBlockType)

                /**
                 * Plugin ids starting with `$segment`.
                 */
                val `$extendedType`.`${parts.last()}`: `$segmentTypeName`
                    get() = `$segmentTypeName`(${if (extendedType == pluginsBlockType) "this" else "plugins"})
            """)
            newLine()
        }

        fun BufferedWriter.writeExtensionsFor(plugin: PluginData) {
            val pluginIdType = pluginIdTypeFor(plugin.id.split('.'))
            appendCode("""
                class `$pluginIdType`(internal val plugins: $pluginsBlockType, plugin: PluginDependencySpec) : PluginDependencySpec by plugin
            """)
            newLine()
            val memberName = plugin.id.substringAfterLast('.')
            appendCode(kdocFor(plugin))
            appendCode("""
                val `$segmentTypeName`.`$memberName`: `$pluginIdType`
                    get() = `$pluginIdType`(plugins, plugins.id("${plugin.id}"))
            """)
            newLine()
            plugin.versions.forEach {
                appendCode("""
                    /**
                     * Version `$it` of `${plugin.id}`.
                     */
                    val `$pluginIdType`.`v${it.replace('.', '_')}`: $pluginExtensionType
                        get() = version("$it")
                """)
                newLine()
            }
        }

        outputFileFor(segment).parentedBufferedWriter().use { writer ->
            tailrec fun writeNext() {
                val event = eventQ.take()
                if (event is Event.OnNext) {
                    writer.writeExtensionsFor(event.plugin)
                    writeNext()
                }
            }

            writer.writeFileHeader()
            writeNext()
        }
    }

    private fun segmentOf(id: String, segmentSize: Int): String =
        joinSegmentOf(id.split('.'), segmentSize)

    private fun joinSegmentOf(parts: List<String>, segmentSize: Int) =
        parts.take(segmentSize).joinToString(separator = ".")

    private fun pluginIdTypeFor(parts: List<String>) =
        typeNameFor(parts, "DependencySpec")

    private fun pluginGroupTypeFor(parts: List<String>) =
        typeNameFor(parts, "PluginGroup")

    private fun typeNameFor(parts: List<String>, suffix: String) =
        (parts.joinToString(separator = "") { it.capitalize() } + suffix).replace('-', '$')

    private fun ExecutorService.spawn(act: (EventQ) -> Unit): Actor {
        val eventQ = ArrayBlockingQueue<Event>(32)
        val future = submit { act(eventQ) }
        return eventQ to future
    }

    private fun postTo(actor: Actor, event: Event.OnNext) {
        val (eventQ, _) = actor
        eventQ.put(event)
    }

    private fun requestAndAwaitCompletionOf(actors: Collection<Actor>) {
        actors.forEach {
            requestCompletionOf(it)
        }
        actors.forEach {
            awaitCompletionOf(it)
        }
    }

    private fun requestCompletionOf(actor: Actor) {
        val (eventQ, _) = actor
        eventQ.put(Event.OnCompleted)
    }

    private fun awaitCompletionOf(actor: Actor) {
        val (_, future) = actor
        future.get()
    }

    private val packageName = "org.gradle.script.lang.kotlin"

    fun outputFileFor(segment: String) =
        File(outputDir, "$segment.kt")

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

    private fun kdocFor(data: PluginData): String = """
        /**
         * ${data.description}
         *
         * Visit the [plugin website](${data.website}) for more information.
         */
        """

    private fun java.io.BufferedWriter.appendCode(possiblyIndentedCode: String) =
        appendln(possiblyIndentedCode.replaceIndent())

    val pluginsBlockType = PluginDependenciesSpec::class.simpleName!!

    val pluginExtensionType = PluginDependencySpec::class.simpleName!!
}
