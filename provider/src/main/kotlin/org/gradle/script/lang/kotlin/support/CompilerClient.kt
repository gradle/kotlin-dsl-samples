package org.gradle.script.lang.kotlin.support

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.daemon.client.CompileServiceSession
import org.jetbrains.kotlin.daemon.client.DaemonReportingTargets
import org.jetbrains.kotlin.daemon.client.KotlinCompilerClient
import org.jetbrains.kotlin.daemon.common.CompileService
import org.jetbrains.kotlin.daemon.common.CompilerId
import org.jetbrains.kotlin.daemon.common.DaemonJVMOptions
import org.jetbrains.kotlin.daemon.common.configureDaemonJVMOptions
import org.jetbrains.kotlin.daemon.common.DaemonOptions
import java.io.Closeable
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

typealias JarsProvider = () -> Collection<File>

class CompilerClient(
    val gradleApiJarsProvider: JarsProvider,
    messageCollector: MessageCollector?
) : Closeable {

    companion object {
        fun configure(compilerId: CompilerId, daemonJVMOptions: DaemonJVMOptions, daemonOptions: DaemonOptions) = lock.write {
            _compilerId = compilerId
            _daemonJVMOptions = daemonJVMOptions
            _daemonOptions = daemonOptions
        }

        fun configure(compilerClasspath: Iterable<File>) {
            val daemonJVMOptions = configureDaemonJVMOptions(
                inheritMemoryLimits = true,
                inheritOtherJvmOptions = false,
                inheritAdditionalProperties = true)
            configure(CompilerId.makeCompilerId(compilerClasspath.distinct().sortedDescending()),
                daemonJVMOptions,
                DaemonOptions())
        }

        private val lock = ReentrantReadWriteLock()
        private var _compilerId: CompilerId? = null
        private var _daemonJVMOptions: DaemonJVMOptions? = null
        private var _daemonOptions: DaemonOptions? = null
        private var sharedDaemon: CompileService? = null
        private var clientAliveFlagFile: File? = null
    }

    val daemon: CompileService get() = serviceWithSession.compileService
    val sessionId: Int get() = serviceWithSession.sessionId

    val compilerClasspath: ClassPath by lazy {
        filteredKotlinClassPath { "kotlin-compiler-\\d.*\\.jar".toRegex().matches(it.name) }
    }

    val templateDepsClasspath: ClassPath by lazy {
        filteredKotlinClassPath { it.name.startsWith("gradle-script-kotlin-") }
    }

    val compilerPluginsClasspath: ClassPath by lazy {
        filteredKotlinClassPath {
            it.name.startsWith(SAM_WITH_RECEIVER_JAR_PREFIX) || it.name.startsWith(SOURCE_SECTIONS_JAR_PREFIX)
        }
    }

    private fun filteredKotlinClassPath(filter: (File) -> Boolean): ClassPath = DefaultClassPath.of(
        gradleApiJarsProvider()
            .mapNotNull { if (it.isFile) it.parentFile else null }
            .distinct()
            .flatMap { it.listFiles().filter(filter) }
            .distinct())

    private val serviceWithSession: CompileServiceSession by lazy {
        lock.read {
            if (_compilerId == null) {
                configure(compilerClasspath.asFiles)
            }
            val d = sharedDaemon
            val sidRes = d?.leaseCompileSession(null)
            if (sidRes != null && sidRes is CompileService.CallResult.Good) {
                CompileServiceSession(d, sidRes.get())
            } else {
                lock.write {
                    clientAliveFlagFile?.delete()
                    clientAliveFlagFile = createTempFile("gradle-script-kotlin", ".alive").apply {
                        deleteOnExit()
                    }
                    // seems required for RMI callback connection from daemon
                    // TODO: investigate and consider removing it
                    System.setProperty("java..security.policy", "")
                    val reportingTargets = messageCollector?.let { DaemonReportingTargets(messageCollector = it) } ?: DaemonReportingTargets(out = System.out)
                    with(KotlinCompilerClient.connectAndLease(_compilerId!!, clientAliveFlagFile!!, _daemonJVMOptions!!,
                                                              _daemonOptions!!, reportingTargets, autostart = true, leaseSession = true)!!)
                    {
                        sharedDaemon = compileService
                        org.jetbrains.kotlin.daemon.client.CompileServiceSession(compileService, sessionId)
                    }
                }
            }
        }
    }

    override fun close() {
        sharedDaemon?.releaseCompileSession(sessionId)
    }
}
