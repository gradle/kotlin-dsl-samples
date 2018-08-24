

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.invoke


class GreetPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        extensions.create("greetings", GreetingsExtension::class)
        tasks {
            register("greet") {
                group = "sample"
                description = "Prints a description of ${project.name}."
                doLast {
                    println("I'm ${project.name}.")
                }
            }
        }
    }
}

open class GreetingsExtension {
    var message = "hey"
}
