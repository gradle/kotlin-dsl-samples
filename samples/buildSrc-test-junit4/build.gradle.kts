import tools.sayHelloTo

tasks {
    val hello by creating {
        description = "this task uses a custom function defined in buildSrc"
        group = "sample"

        doLast {
            println(sayHelloTo("Gradle"))
        }
    }
}
