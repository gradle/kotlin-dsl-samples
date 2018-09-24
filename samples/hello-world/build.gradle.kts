plugins {
    application
    id("my-java-project")
    `maven-publish`
}

application {
    mainClassName = "samples.HelloWorld"
}
