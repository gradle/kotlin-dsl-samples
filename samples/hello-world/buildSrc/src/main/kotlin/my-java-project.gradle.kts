the<JavaPluginExtension>().java8Compatibility()

val compile by configurations
val testCompile by configurations

dependencies {
    testCompile("junit:junit:4.12")
}

repositories {
    jcenter()
}

val myConfiguration by configurations.creating {
    extendsFrom(compile)
}

dependencies {
    myConfiguration("junit:junit:4.12")
}
