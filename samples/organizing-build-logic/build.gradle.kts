
tasks {
    "answer" {
        doLast {
            println(answerTheUltimateQuestionAboutLifeTheUniverseAndEverything())
        }
    }
}

withHelloTask()

apply<MyPlugin>()
