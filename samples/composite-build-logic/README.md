composite-build-logic
=====================

Demonstrates how to use [Composite Builds](https://docs.gradle.org/current/userguide/composite_builds.html)
for build logic.

The build in the [`build-logic`](./build-logic) directory publishes a Gradle Plugin.
The build in this directory includes the `build-logic` build and uses the Gradle Plugin for its build logic.

Run with:

    ./gradlew sample

See [settings.gradle.kts](./settings.gradle.kts) to see how to include builds and do dependency substitution.
