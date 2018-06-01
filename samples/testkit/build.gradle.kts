plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

gradlePlugin {
    (plugins) {
        create("greet") {
            id = "greet"
            implementationClass = "samples.GreetPlugin"
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.12")
}

repositories {
    gradlePluginPortal()
}
