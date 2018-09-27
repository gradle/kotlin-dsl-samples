plugins {
    application
    id("my-plugin")
}

application {
    mainClassName = "samples.HelloWorld"
}

dependencies {
    implementation("junit:junit:4.12")
}

repositories {
    jcenter()
}