import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*

class HelloTask : my.DefaultTask() {

    init {
        group = "My"
        description = "Prints a description of ${project.name}."
    }

    @TaskAction
    fun run() {
        println("I'm ${project.name}")
    }
}

/**
 * Declares a [HelloTask] named `hello`.
 */
fun Project.withHelloTask() =
    tasks.register("hello", HelloTask::class)

