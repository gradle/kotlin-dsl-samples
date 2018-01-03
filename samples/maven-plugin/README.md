# maven-plugin sample

Demonstrates the use of the `withConvention` and `withGroovyBuilder` utility extensions for configuring the `uploadArchives` task to [deploy to a Maven repository](https://docs.gradle.org/current/userguide/maven_plugin.html#sec:deploying_to_a_maven_repository) with a [custom pom](./build.gradle.kts#L42).

Also demonstrates how to publish accompanying source and javadoc jars.

## Running the sample

Execute the `uploadAchives` tasks:

    ./gradlew uploadArchives

List the resulting publications:

    find build/m2
