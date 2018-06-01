plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

gradlePlugin {
    (plugins) {
        create("greet-plugin") {
            id = "greet"
            implementationClass = "GreetPlugin"
        }
    }
}
