package plugins

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*


open class SampleTask : DefaultTask() {

    @Input
    val message = project.objects.property<String>()

    @TaskAction
    fun sample() {
        logger.lifecycle(message.get())
    }
}
