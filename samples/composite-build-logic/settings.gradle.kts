val buildLogicGroup = "composite-build-logic"

listOf("shared", "plugins").forEach { included ->
    includeBuild("gradle/$included") {
        dependencySubstitution {
            substitute(module("$buildLogicGroup:$included")).with(project(":"))
        }
    }
}

// Work around for https://github.com/gradle/gradle-native/issues/522
pluginManagement {
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "plugins.sample-plugin" -> useModule("$buildLogicGroup:plugins:latest-integration")
            }
        }
    }
}
