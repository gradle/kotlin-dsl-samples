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

import groovy.json.JsonOutput.toJson

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import org.gradle.internal.logging.progress.ProgressLoggerFactory

import org.jsoup.Jsoup.connect
import org.jsoup.nodes.Document
import java.io.BufferedWriter

import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors.newCachedThreadPool
import java.util.concurrent.TimeUnit

import javax.inject.Inject

import kotlin.concurrent.thread

/**
 * Builds a line-separated JSON file containing an entry for each and every plugin found in plugins.gradle.org at the
 * moment of execution.
 *
 * The plugins are found by scraping plugin ids from the search page, one page number at a time, until no more plugin
 * ids are found.
 *
 * [PluginData] is then scraped from each specific plugin page and written to the index file.
 */
open class FetchPluginPortalIndex : DefaultTask() {

    override fun getDescription(): String =
        "Builds an index of all plugins currently published to plugins.gradle.org."

    @get:OutputFile
    var outputFile: File? = null

    @get:Inject
    open val progressLoggerFactory: ProgressLoggerFactory
        get() = throw NotImplementedError()

    @Suppress("unused")
    @TaskAction
    fun fetch() {
        val eventQ = ArrayBlockingQueue<Event>(1024)
        asynchronouslyFetchPluginsInto(eventQ)
        outputFileWriter().use {
            writePluginsTo(it, eventQ)
        }
    }

    private sealed class Event {
        data class OnNext(val plugin: PluginData) : Event()
        data class OnCompleted(val totalPageCount: Int) : Event()
    }

    private fun asynchronouslyFetchPluginsInto(eventQ: BlockingQueue<Event>) {
        thread {
            val scheduler = newCachedThreadPool()
            var page = 0
            try {
                while (true) {
                    val pluginIds = fetchPluginIdsFromPage(page)
                    if (pluginIds.isEmpty()) break
                    pluginIds.forEach { pluginId ->
                        scheduler.submit {
                            eventQ.put(
                                Event.OnNext(fetchPluginDataFor(pluginId)))
                        }
                    }
                    page++
                }
            } finally {
                scheduler.shutdownAndAwaitTermination()
                eventQ.put(Event.OnCompleted(page))
            }
        }
    }

    private fun writePluginsTo(writer: BufferedWriter, eventQ: BlockingQueue<Event>) {
        val operation = startProgressLoggerOperation()
        var pluginCount = 0
        loop@ while (true) {
            val event = eventQ.take()
            when (event) {
                is Event.OnNext -> {
                    pluginCount++
                    writer.appendln(toJson(event.plugin))
                    operation.progress("$pluginCount: ${event.plugin.id}")
                }
                is Event.OnCompleted -> {
                    operation.completed("$pluginCount plugins fetched after processing ${event.totalPageCount} pages.")
                    break@loop
                }
            }
        }
    }

    private fun startProgressLoggerOperation() =
        progressLoggerFactory.newOperation(FetchPluginPortalIndex::class.java).apply {
            start("Fetching Plugin Portal index", "Fetching initial page...")
        }

    private fun outputFileWriter() = outputFile!!.parentedBufferedWriter()

    private fun fetchPluginIdsFromPage(page: Int): List<String> =
        get("search?page=$page").select("h3.plugin-id a").map { it.text() }

    private fun fetchPluginDataFor(pluginId: String): PluginData {
        val doc = get("plugin/$pluginId")
        val description = doc.select("p.description-text").text()
        val website = doc.select("p.website").text()
        val latestVersion = doc.select("div.version-info h3").text().split(" ")[1]
        val otherVersions = doc.select("div.other-versions ul.dropdown-menu li").map { it.text() }
        return PluginData(pluginId, description, website, listOf(latestVersion) + otherVersions)
    }

    private fun get(path: String): Document {
        tailrec fun retry(remainingAttempts: Int): Document {
            try {
                return connect("https://plugins.gradle.org/" + path).get()
            } catch (e: Exception) {
                if (remainingAttempts == 0) throw e
            }
            Thread.sleep(500)
            return retry(remainingAttempts - 1)
        }
        return retry(3)
    }
}

internal
fun ExecutorService.shutdownAndAwaitTermination() {
    shutdown()
    awaitTermination(1, TimeUnit.DAYS)
}

internal
fun File.parentedBufferedWriter(): BufferedWriter {
    parentFile.mkdirs()
    return bufferedWriter()
}

