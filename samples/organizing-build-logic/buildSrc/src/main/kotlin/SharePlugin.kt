import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

open class MyPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.run {
            tasks {
                "goodbye" {
                    group = "My"
                    description = "Goodbye!"
                    doLast {
                        println("Remember me and smile, for it's better to forget than to remember me and cry.")
                    }
                }
            }
        }
    }
}