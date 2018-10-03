plugins {
    application
    kotlin("jvm") version "1.3.0-rc-146"
}

application {
    mainClassName = "samples.HelloWorldKt"
}

dependencies {
    compile(kotlin("stdlib"))
}

repositories {
    jcenter()
}
