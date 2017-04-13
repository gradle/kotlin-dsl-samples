plugins {
    application
}

application {
    mainClassName = "cli.Main"
}

dependencies {
    compile(project(":core"))
}
