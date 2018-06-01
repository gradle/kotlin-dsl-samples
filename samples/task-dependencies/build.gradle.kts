
tasks {

    val hello by creating { // refactor friendly task definition
        doLast { println("Hello!") }
    }

    create("goodbye") {
        dependsOn(hello)  // dependsOn task reference
        doLast { println("Goodbye!") }
    }

    create("chat") {
        dependsOn("goodbye") // dependsOn task name
    }

    create("mixItUp") {
        dependsOn(hello, "goodbye")
    }
}

defaultTasks("chat")
