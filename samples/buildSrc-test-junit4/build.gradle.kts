import tools.sayHelloTo

tasks.register("hello") {
    description = "this task uses a custom function defined in buildSrc"
    group = "sample"

    doLast {
        println(sayHelloTo("Gradle"))
    }
}
