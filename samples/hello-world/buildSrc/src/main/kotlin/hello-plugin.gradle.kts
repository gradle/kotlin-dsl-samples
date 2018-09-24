val hello by tasks.registering {
    doLast {
        println("Hello world!")
    }
}
